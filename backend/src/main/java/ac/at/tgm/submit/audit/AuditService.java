package ac.at.tgm.submit.audit;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.web.ApiDtos.AuditEntryResponse;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final RecordStore store;

    public AuditService(RecordStore store) {
        this.store = store;
    }

    @Transactional
    public void record(AuthenticatedUser actor, String action, String entityType, UUID entityId) {
        ObjectNode payload = store.object().put("actorId", actor.id().toString())
                .put("actorUsername", actor.username()).put("action", action)
                .put("entityType", entityType).put("entityId", entityId.toString())
                .put("occurredAt", Instant.now().toString());
        store.createAudit(actor.id().toString(), entityId.toString(), action, payload, entityType);
    }

    @Transactional
    public void recordSystem(String action, String entityType, UUID entityId) {
        ObjectNode payload = store.object().put("actorUsername", "SYSTEM").put("action", action)
                .put("entityType", entityType).put("entityId", entityId.toString())
                .put("occurredAt", Instant.now().toString());
        store.createAudit(null, entityId.toString(), action, payload, entityType);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> list(AuthenticatedUser caller, int page, int size) {
        if (caller.role() == Role.STUDENT) {
            throw new org.springframework.security.access.AccessDeniedException("Audit access is restricted.");
        }
        Page<StoredRecord> records = store.page("audit", null, null, PageRequest.of(page, size));
        return new PageResponse<>(records.map(this::response).getContent(), records.getNumber(),
                records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> forEntity(AuthenticatedUser caller, String entityType, UUID entityId,
            int page, int size) {
        if (caller.role() == Role.STUDENT) {
            throw new org.springframework.security.access.AccessDeniedException("Audit access is restricted.");
        }
        Page<StoredRecord> records = store.repository().findByTypeAndReferenceTypeAndParentId(
                "audit", entityType, entityId.toString(), PageRequest.of(page, size));
        return new PageResponse<>(records.map(this::response).getContent(), records.getNumber(),
                records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    private AuditEntryResponse response(StoredRecord record) {
        ObjectNode event = store.parse(record);
        return new AuditEntryResponse(event.hasNonNull("actorId") ? event.path("actorId").asText() : null,
                event.path("actorUsername").asText(), event.path("action").asText(),
                event.path("entityType").asText(), event.path("entityId").asText(),
                event.path("occurredAt").asText());
    }
}
