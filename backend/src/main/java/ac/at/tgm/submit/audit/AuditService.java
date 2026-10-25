package ac.at.tgm.submit.audit;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.web.ApiDtos.AuditEntryResponse;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private static final String SYSTEM_USERNAME = "SYSTEM";
    private final RecordStore store;
    private final AuditContextResolver contextResolver;

    public AuditService(RecordStore store, AuditContextResolver contextResolver) {
        this.store = store;
        this.contextResolver = contextResolver;
    }

    @Transactional
    public void record(AuthenticatedUser actor, String action, String entityType, UUID entityId) {
        ObjectNode payload = store.object().put("actorId", actor.id().toString())
                .put("actorUsername", actor.username()).put("action", action)
                .put("entityType", entityType).put("entityId", entityId.toString())
                .put("occurredAt", Instant.now().toString());
        contextResolver.snapshotActor(payload, actor);
        String label = contextResolver.snapshotResource(entityType, entityId);
        if (label != null) {
            payload.put("resourceLabel", label);
        }
        store.createAudit(actor.id().toString(), entityId.toString(), action, payload, entityType);
    }

    @Transactional
    public void recordSystem(String action, String entityType, UUID entityId) {
        ObjectNode payload = store.object().put("actorUsername", SYSTEM_USERNAME).put("action", action)
                .put("entityType", entityType).put("entityId", entityId.toString())
                .put("occurredAt", Instant.now().toString());
        String label = contextResolver.snapshotResource(entityType, entityId);
        if (label != null) {
            payload.put("resourceLabel", label);
        }
        store.createAudit(null, entityId.toString(), action, payload, entityType);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> list(AuthenticatedUser caller, int page, int size) {
        requireStaff(caller);
        Page<StoredRecord> records = store.page("audit", null, null, PageRequest.of(page, size));
        return response(records);
    }

    @Transactional(readOnly = true)
    public PageResponse<AuditEntryResponse> forEntity(AuthenticatedUser caller, String entityType, UUID entityId,
            int page, int size) {
        requireStaff(caller);
        Page<StoredRecord> records = store.repository().findByTypeAndReferenceTypeAndParentId(
                "audit", entityType, entityId.toString(), PageRequest.of(page, size));
        return response(records);
    }

    private PageResponse<AuditEntryResponse> response(Page<StoredRecord> records) {
        List<AuditEntryResponse> content = contextResolver.responses(records.getContent());
        return new PageResponse<>(content, records.getNumber(), records.getSize(), records.getTotalElements(),
                records.getTotalPages());
    }

    private void requireStaff(AuthenticatedUser caller) {
        if (caller.role() == Role.STUDENT) {
            throw new org.springframework.security.access.AccessDeniedException("Audit access is restricted.");
        }
    }
}
