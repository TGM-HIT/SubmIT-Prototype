package com.flightlog.audit;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.security.Role;
import com.flightlog.web.ApiDtos.PageResponse;
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
    public PageResponse<ObjectNode> list(AuthenticatedUser caller, int page, int size) {
        if (caller.role() == Role.STUDENT) {
            throw new org.springframework.security.access.AccessDeniedException("Audit access is restricted.");
        }
        Page<StoredRecord> records = store.page("audit", null, null, PageRequest.of(page, size));
        return new PageResponse<>(records.map(store::parse).getContent(), records.getNumber(),
                records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<ObjectNode> forEntity(AuthenticatedUser caller, String entityType, UUID entityId,
            int page, int size) {
        if (caller.role() == Role.STUDENT) {
            throw new org.springframework.security.access.AccessDeniedException("Audit access is restricted.");
        }
        Page<StoredRecord> records = store.repository().findByTypeAndReferenceTypeAndParentId(
                "audit", entityType, entityId.toString(), PageRequest.of(page, size));
        return new PageResponse<>(records.map(store::parse).getContent(), records.getNumber(),
                records.getSize(), records.getTotalElements(), records.getTotalPages());
    }
}
