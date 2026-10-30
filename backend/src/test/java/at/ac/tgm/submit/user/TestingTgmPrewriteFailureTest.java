package at.ac.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.security.Role;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testingtgmprewrite;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_MINIMAL_FIXTURES=true"
})
@ActiveProfiles("testing")
@Transactional
class TestingTgmPrewriteFailureTest {
    private static final Path INVENTED_PATH = Path.of("invented-tgm-prewrite.xlsx");

    @Autowired private Environment environment;
    @Autowired private TgmWorkbookReader reader;
    @Autowired private TgmWorkbookImportPlanner planner;
    @Autowired private TgmWorkbookImportWriter writer;
    @Autowired private UserAccountRepository users;
    @Autowired private StudentClassRepository classes;
    @Autowired private RecordStore store;
    @Autowired private EntityManager entities;
    @TempDir Path temporaryDirectory;

    @Test
    void preexistingLoginConflictDoesNotWriteOrOverwriteAnyState() {
        // Different display identity isolates the login guard.
        seedUnrelatedStudent("FixtureÄSolo", "Unrelated Existing Identity", "Unrelated Class");
        assertRejectedUnchanged(fixtureImporter(), INVENTED_PATH, "login conflict",
                "login conflicts with an account not owned by this import", "4aHIT", "row 2");
    }

    @Test
    void preexistingDisplayIdentityConflictDoesNotWriteOrOverwriteAnyState() {
        // Distinct login and class isolate the normalized display-identity guard.
        seedUnrelatedStudent("unrelated-existing-login", "Fixture   Ä Solo", "Unrelated Class");
        assertRejectedUnchanged(fixtureImporter(), INVENTED_PATH, "display conflict",
                "display identity conflicts with an existing account", "4aHIT", "row 2");
    }

    @Test
    void targetClassAssignedToUnrelatedStudentDoesNotWriteAnyState() {
        // Neither the login nor display identity collides, so rejection must reach the class guard.
        seedUnrelatedStudent("unrelated-existing-login", "Unrelated Existing Identity", "4aHIT");
        assertRejectedUnchanged(fixtureImporter(), INVENTED_PATH, "class conflict",
                "class is already assigned to unrelated student accounts", "4aHIT", "row 1");
    }

    @Test
    void malformedSourceFailsThroughImporterWithoutPartialState() {
        seedUnrelatedStudent("unrelated-existing-login", "Unrelated Existing Identity", "Unrelated Class");
        assertRejectedUnchanged(importer(reader), INVENTED_PATH, "not an XLSX archive",
                "workbook", INVENTED_PATH.toString());
    }

    @Test
    void missingSourceFailsThroughImporterReadAndImportPipelineWithoutPartialState() {
        seedUnrelatedStudent("unrelated-existing-login", "Unrelated Existing Identity", "Unrelated Class");
        TestingTgmWorkbookImporter importer = importer(reader);
        Path missing = temporaryDirectory.resolve("missing-invented.xlsx");
        DatabaseSnapshot before = snapshot();
        // Same read-then-import sequence as ApplicationRunner.run; no invented fallback bytes.
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> importer.importSource(missing, importer.readSource(missing)));
        assertTrue(failure.getMessage().contains("Unable to read required local TGM workbook"));
        assertTrue(failure.getMessage().contains(missing.toString()));
        assertUnchanged(before);
    }

    private void assertRejectedUnchanged(TestingTgmWorkbookImporter importer, Path source, String bytes,
            String... diagnostics) {
        DatabaseSnapshot before = snapshot();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("prewrite-test", "not-a-secret"));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> importer.importSource(source, bytes.getBytes(StandardCharsets.UTF_8)));
        for (String diagnostic : diagnostics) {
            assertTrue(failure.getMessage().contains(diagnostic), failure.getMessage());
        }
        assertTrue(failure.getMessage().contains(source.toString()));
        for (TgmWorkbookReader.StudentRow row : TgmWorkbookFixtures.profileFixture().students()) {
            assertFalse(failure.getMessage().contains(row.displayName()));
            assertFalse(failure.getMessage().contains(row.username()));
        }
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertUnchanged(before);
    }

    private void assertUnchanged(DatabaseSnapshot before) {
        // Flush and detach to compare reloaded DB values, not mutable managed/returned entities.
        // Assertions run before the test transaction rolls back: rollback cannot hide partial writes.
        assertEquals(before, snapshot());
        assertTrue(store.repository().findByTypeAndParentId("testing_tgm_workbook_import", "source").isEmpty());
        assertTrue(store.repository().findByTypeAndParentId("application_period", null).isEmpty());
    }

    private void seedUnrelatedStudent(String username, String displayName, String className) {
        StudentClassEntity studentClass = classes.save(new StudentClassEntity(UUID.randomUUID(), className));
        UserAccount account = new UserAccount(UUID.randomUUID(), username, displayName,
                users.findByUsername("student01").orElseThrow().getPasswordHash(), Role.STUDENT);
        account.setStudentClass(studentClass);
        users.save(account);
        store.create("unrelated_prewrite_record", account.getId().toString(), studentClass.getId().toString(),
                "PRESERVED", store.object().put("invented", "unchanged nested state")
                        .set("nested", store.object().put("value", 37)));
    }

    private TestingTgmWorkbookImporter fixtureImporter() {
        TgmWorkbookReader sourceReader = mock(TgmWorkbookReader.class);
        when(sourceReader.read(any(Path.class), any(byte[].class))).thenReturn(TgmWorkbookFixtures.profileFixture());
        return importer(sourceReader);
    }

    private TestingTgmWorkbookImporter importer(TgmWorkbookReader sourceReader) {
        return new TestingTgmWorkbookImporter(environment, sourceReader, planner, writer);
    }

    private DatabaseSnapshot snapshot() {
        entities.flush();
        entities.clear();
        Map<UUID, UserSnapshot> accounts = users.findAll().stream().collect(Collectors.toMap(UserAccount::getId,
                user -> new UserSnapshot(user.getUsername(), user.getDisplayName(), user.getPasswordHash(),
                        user.getRoles(), user.getLanguage(), user.getStudentClass() == null
                                ? null : user.getStudentClass().getId(),
                        user.getElectiveSubjects().stream().map(ElectiveSubjectEntity::getId).sorted().toList())));
        Map<UUID, String> classNames = classes.findAll().stream()
                .collect(Collectors.toMap(StudentClassEntity::getId, StudentClassEntity::getName));
        Map<UUID, RecordSnapshot> records = store.repository().findAll().stream()
                .collect(Collectors.toMap(StoredRecord::getId, record -> new RecordSnapshot(record.getType(),
                        record.getOwnerId(), record.getParentId(), record.getStatus(), record.getReferenceType(),
                        record.getPayload(), record.getCreatedAt())));
        assertTrue(records.values().stream().anyMatch(record -> record.type().equals("grading_rubric")));
        assertTrue(records.values().stream().anyMatch(record -> record.type().equals("unrelated_prewrite_record")));
        return new DatabaseSnapshot(accounts, classNames, records);
    }

    private record UserSnapshot(String username, String displayName, String passwordHash, List<Role> roles,
            AccountLanguage language, UUID classId, List<UUID> electiveIds) {
    }

    private record RecordSnapshot(String type, String ownerId, String parentId, String status, String referenceType,
            String payload, Instant createdAt) {
    }

    private record DatabaseSnapshot(Map<UUID, UserSnapshot> users, Map<UUID, String> classes,
            Map<UUID, RecordSnapshot> records) {
    }
}
