package ac.at.tgm.submit.audit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.user.ElectiveSubjectRepository;
import ac.at.tgm.submit.user.StudentClassRepository;
import ac.at.tgm.submit.user.UserAccountRepository;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

class AuditContextResolverTest {
    @Test
    void repositoryFailuresPropagateInsteadOfBeingReportedAsUnavailable() {
        RecordStore store = mock(RecordStore.class);
        UserAccountRepository users = mock(UserAccountRepository.class);
        StudentClassRepository classes = mock(StudentClassRepository.class);
        ElectiveSubjectRepository subjects = mock(ElectiveSubjectRepository.class);
        StoredRecord record = mock(StoredRecord.class);
        UUID actorId = UUID.randomUUID();
        ObjectNode payload = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode()
                .put("actorId", actorId.toString()).put("actorUsername", "legacy-user")
                .put("action", "LEGACY_ACTION").put("entityType", "unsupported-resource")
                .put("entityId", UUID.randomUUID().toString()).put("occurredAt", Instant.now().toString());
        when(store.parse(record)).thenReturn(payload);
        when(users.findAllById(Set.of(actorId))).thenThrow(
                new DataAccessResourceFailureException("Test repository outage"));

        AuditContextResolver resolver = new AuditContextResolver(store, users, classes, subjects);
        assertThrows(DataAccessResourceFailureException.class, () -> resolver.responses(List.of(record)));
    }
}
