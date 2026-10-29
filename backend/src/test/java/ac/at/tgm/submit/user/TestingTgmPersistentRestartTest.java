package ac.at.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ac.at.tgm.submit.SubmITlication;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

class TestingTgmPersistentRestartTest {
    private static final Path INVENTED_PATH = Path.of("invented-persistent-tgm-source.xlsx");

    @TempDir Path temporaryDirectory;

    @Test
    void persistedRestartPreservesDatasetEditsHashesDatesMembershipAndHistoryAndRejectsChangedSource() {
        String databasePath = temporaryDirectory.resolve("persistent-tgm-db").toAbsolutePath().toString();
        DurableSnapshot afterUserEdit;
        try (ConfigurableApplicationContext first = start(databasePath)) {
            UserAccountRepository users = first.getBean(UserAccountRepository.class);
            UserAccount solo = users.findByUsername("FixtureÄSolo").orElseThrow();
            String passwordHash = solo.getPasswordHash();
            UUID accountId = solo.getId();
            solo.setDisplayName("Persisted invented edit");
            users.saveAndFlush(solo);

            afterUserEdit = snapshot(first);
            assertEquals(accountId, afterUserEdit.users().get("FixtureÄSolo").id());
            assertEquals("Persisted invented edit", afterUserEdit.users().get("FixtureÄSolo").displayName());
            assertEquals(passwordHash, afterUserEdit.users().get("FixtureÄSolo").passwordHash());
            assertTrue(afterUserEdit.records().values().stream()
                    .anyMatch(record -> record.type().equals("applying_group")));
            assertTrue(afterUserEdit.records().values().stream()
                    .anyMatch(record -> record.type().equals("audit")));
        }

        try (ConfigurableApplicationContext restarted = start(databasePath)) {
            DurableSnapshot afterRestart = snapshot(restarted);
            assertEquals(afterUserEdit, afterRestart);

            UserAccount reloaded = restarted.getBean(UserAccountRepository.class)
                    .findByUsername("FixtureÄSolo").orElseThrow();
            assertEquals("Persisted invented edit", reloaded.getDisplayName());
            assertEquals(afterUserEdit.users().get("FixtureÄSolo").passwordHash(), reloaded.getPasswordHash());

            SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.unauthenticated("restart-auth", "not-a-secret"));
            TestingTgmWorkbookImporter importer = restarted.getBean(TestingTgmWorkbookImporter.class);
            IllegalStateException changedSource = assertThrows(IllegalStateException.class,
                    () -> importer.importSource(INVENTED_PATH, "changed-invented-source".getBytes(StandardCharsets.UTF_8)));
            assertTrue(changedSource.getMessage().contains("checksum differs"));
            assertTrue(changedSource.getMessage().contains("fresh disposable database"));
            assertTrue(SecurityContextHolder.getContext().getAuthentication() == null);
            assertEquals(afterUserEdit, snapshot(restarted));
        }
    }

    private ConfigurableApplicationContext start(String databasePath) {
        String dataSource = "jdbc:h2:file:" + databasePath + ";DB_CLOSE_ON_EXIT=FALSE";
        return new SpringApplicationBuilder(SubmITlication.class, InventedSourceConfiguration.class)
                .profiles("testing", "testing-tgm")
                .properties(
                        "spring.datasource.url=" + dataSource,
                        "spring.jpa.hibernate.ddl-auto=update",
                        "spring.main.web-application-type=servlet",
                        "server.port=0",
                        "spring.main.banner-mode=off",
                        "DADB_TESTING_MINIMAL_FIXTURES=false",
                        "DADB_TESTING_ALLOCATION_PIN_PREVIEW=true",
                        "DADB_TESTING_APPLYING_GROUP_PREVIEW=true",
                        "DADB_TESTING_ELIGIBLE_FORMAL_PROJECTS_PREVIEW=true",
                        "DADB_TESTING_FORMAL_PROPOSAL_SEARCH_PREVIEW=true",
                        "DADB_TESTING_GRADING_SELECTION_PREVIEW=true",
                        "dadb.testing.applying-group-preview=true",
                        "dadb.testing.eligible-formal-project-preview=true",
                        "dadb.testing.formal-proposal-search-preview=true",
                        "dadb.testing.grading-selection-preview=true")
                .run();
    }

    private DurableSnapshot snapshot(ConfigurableApplicationContext context) {
        RecordStore store = context.getBean(RecordStore.class);
        UserAccountRepository users = context.getBean(UserAccountRepository.class);
        StudentClassRepository classes = context.getBean(StudentClassRepository.class);
        PasswordEncoder passwords = context.getBean(PasswordEncoder.class);
        Map<UUID, RecordSnapshot> records = store.repository().findAll().stream()
                .collect(Collectors.toMap(StoredRecord::getId, record -> new RecordSnapshot(record.getType(),
                        record.getOwnerId(), record.getParentId(), record.getStatus(), record.getReferenceType(),
                        record.getPayload(), record.getCreatedAt())));
        Map<String, UserSnapshot> accounts = users.findAll().stream().collect(Collectors.toMap(
                UserAccount::getUsername, account -> new UserSnapshot(account.getId(), account.getDisplayName(),
                        account.getPasswordHash(), List.copyOf(account.getRoles()),
                        account.getStudentClass() == null ? null
                                : classes.findById(account.getStudentClass().getId()).orElseThrow().getName())));
        Map<String, UUID> classIds = classes.findAll().stream()
                .collect(Collectors.toMap(StudentClassEntity::getName, StudentClassEntity::getId));
        assertTrue(users.findByUsername("FixtureÄSolo").isPresent());
        assertTrue(passwords.matches(TestingAccountsConfiguration.DEFAULT_PASSWORD,
                users.findByUsername("FixtureÄSolo").orElseThrow().getPasswordHash()));
        return new DurableSnapshot(records, accounts, classIds);
    }

    private record RecordSnapshot(String type, String ownerId, String parentId, String status, String referenceType,
            String payload, Instant createdAt) {
    }

    private record UserSnapshot(UUID id, String displayName, String passwordHash, List<?> roles, String className) {
    }

    private record DurableSnapshot(Map<UUID, RecordSnapshot> records, Map<String, UserSnapshot> users,
            Map<String, UUID> classes) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class InventedSourceConfiguration {
        @Bean
        @Primary
        TgmWorkbookReader inventedWorkbookReader() {
            TgmWorkbookReader reader = mock(TgmWorkbookReader.class);
            when(reader.read(any(Path.class), any(byte[].class))).thenReturn(TgmWorkbookFixtures.profileFixture());
            return reader;
        }
    }
}
