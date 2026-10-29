package ac.at.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testingtgmrollback;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_MINIMAL_FIXTURES=true"
})
@ActiveProfiles("testing")
class TestingTgmRollbackTest {
    private static final Path INVENTED_PATH = Path.of("invented-tgm-rollback.xlsx");

    @Autowired private Environment environment;
    @Autowired private TgmWorkbookImportPlanner planner;
    @Autowired private TgmWorkbookImportWriter writer;
    @Autowired private UserAccountRepository users;
    @Autowired private StudentClassRepository classes;
    @Autowired private RecordStore store;
    @Autowired private PlatformTransactionManager transactions;
    @MockitoSpyBean private TgmApplicationImportWriter applications;

    @Test
    void failureAfterGroupHistoryWritesRollsBackAllImportTypesAndClearsAuthentication() {
        Map<UUID, RecordSnapshot> beforeRecords = recordSnapshots();
        Set<UUID> beforeUsers = users.findAll().stream().map(UserAccount::getId).collect(Collectors.toSet());
        Map<String, UUID> beforeClasses = classes.findAll().stream()
                .collect(Collectors.toMap(StudentClassEntity::getName, StudentClassEntity::getId));
        assertTrue(beforeRecords.values().stream().anyMatch(record -> record.type().equals("grading_rubric")));
        assertTrue(beforeUsers.stream().map(users::findById).allMatch(java.util.Optional::isPresent));

        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("forced failure after TGM application and group-history writes");
        }).when(applications).write(any(TgmImportPlan.class),
                any(TgmIdentityImportWriter.ImportedIdentities.class), anyMap(), any(UserAccount.class));

        TgmWorkbookReader sourceReader = mock(TgmWorkbookReader.class);
        when(sourceReader.read(any(Path.class), any(byte[].class))).thenReturn(TgmWorkbookFixtures.profileFixture());
        TestingTgmWorkbookImporter importer = new TestingTgmWorkbookImporter(environment, sourceReader, planner, writer);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("forced-authentication", "not-a-secret"));

        TransactionTemplate transaction = new TransactionTemplate(transactions);
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> transaction.execute(status -> {
            importer.importSource(INVENTED_PATH, "forced-failure".getBytes(StandardCharsets.UTF_8));
            return null;
        }));
        assertTrue(failure.getMessage().contains("forced failure after TGM application"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());

        assertEquals(beforeRecords, recordSnapshots());
        assertEquals(beforeUsers, users.findAll().stream().map(UserAccount::getId).collect(Collectors.toSet()));
        assertEquals(beforeClasses, classes.findAll().stream()
                .collect(Collectors.toMap(StudentClassEntity::getName, StudentClassEntity::getId)));
        assertTrue(users.findByUsername("FixtureÄSolo").isEmpty());
        assertTrue(store.repository().findByTypeAndParentId("testing_tgm_workbook_import", "source").isEmpty());
        assertTrue(store.repository().findByTypeAndParentId("application_period", null).isEmpty());
    }

    private Map<UUID, RecordSnapshot> recordSnapshots() {
        return store.repository().findAll().stream().collect(Collectors.toMap(StoredRecord::getId, record ->
                new RecordSnapshot(record.getType(), record.getOwnerId(), record.getParentId(), record.getStatus(),
                        record.getReferenceType(), record.getPayload(), record.getCreatedAt())));
    }

    private record RecordSnapshot(String type, String ownerId, String parentId, String status, String referenceType,
            String payload, Instant createdAt) {
    }
}
