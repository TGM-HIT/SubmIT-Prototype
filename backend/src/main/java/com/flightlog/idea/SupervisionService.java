package com.flightlog.idea;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.audit.AuditService;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.security.Role;
import com.flightlog.user.UserAccount;
import com.flightlog.user.UserAccountRepository;
import com.flightlog.web.ApiDtos.SupervisionPreferenceResponse;
import com.flightlog.web.ApiDtos.SupervisorResponse;
import com.flightlog.web.ApiException;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SupervisionService {
    private static final String IDEA = "idea";
    private static final String SUPERVISION = "supervision_preference";
    private static final int MAX_NOTE_LENGTH = 2000;

    private final RecordStore store;
    private final AuditService audit;
    private final UserAccountRepository users;
    private final IdeaRules rules;
    private final IdeaResponseMapper mapper;

    public SupervisionService(RecordStore store, AuditService audit, UserAccountRepository users,
            IdeaRules rules, IdeaResponseMapper mapper) {
        this.store = store;
        this.audit = audit;
        this.users = users;
        this.rules = rules;
        this.mapper = mapper;
    }

    @Transactional
    @PreAuthorize("hasRole('TEACHER')")
    public SupervisionPreferenceResponse setPreference(AuthenticatedUser caller, UUID id, JsonNode request) {
        StoredRecord idea = store.repository().findByIdForUpdate(id)
                .filter(record -> IDEA.equals(record.getType()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Idea was not found."));
        ObjectNode payload = rules.object(request);
        String preference = rules.text(payload, "preference");
        if (!List.of("WANTS_TO_SUPERVISE", "WILLING_TO_SUPERVISE", "NOT_AVAILABLE").contains(preference)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported supervision preference.");
        }
        if (payload.hasNonNull("note") && payload.path("note").asText().length() > MAX_NOTE_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "note must not exceed "
                    + MAX_NOTE_LENGTH + " characters.");
        }
        if (List.of("IDEA_DRAFT", "WITHDRAWN", "DECLINED").contains(idea.getStatus())) {
            throw rules.conflict("Supervision preferences are available only for reviewed projects.");
        }
        List<StoredRecord> existing = store.repository()
                .findByTypeAndParentIdAndOwnerIdOrderByCreatedAtDesc(SUPERVISION, id.toString(),
                        caller.id().toString());
        StoredRecord record;
        if (existing.isEmpty()) {
            record = store.create(SUPERVISION, caller.id().toString(), id.toString(), preference, payload);
        } else {
            record = existing.get(0);
            store.update(record, preference, payload);
            existing.stream().skip(1).forEach(store::delete);
        }
        audit.record(caller, "SUPERVISION_PREFERENCE_CHANGED", IDEA, id);
        return mapper.supervisionResponse(record);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public SupervisorResponse assign(AuthenticatedUser caller, UUID id, JsonNode request) {
        findIdea(id);
        UUID teacherId = UUID.fromString(rules.object(request).path("teacherId").asText());
        UserAccount teacher = users.findById(teacherId)
                .orElseThrow(() -> new EntityNotFoundException("Teacher was not found."));
        if (teacher.getRole() != Role.TEACHER) {
            throw rules.conflict("Only teachers can be confirmed as supervisors.");
        }
        StoredRecord preference = store.repository()
                .findFirstByTypeAndParentIdAndOwnerIdOrderByCreatedAtDesc(SUPERVISION, id.toString(),
                        teacherId.toString())
                .orElseThrow(() -> rules.conflict("The teacher has not recorded a supervision preference."));
        if (!List.of("WANTS_TO_SUPERVISE", "WILLING_TO_SUPERVISE").contains(preference.getStatus())) {
            throw rules.conflict("The teacher is not available to supervise this project.");
        }
        ObjectNode assignment = store.object().put("teacherId", teacherId.toString());
        StoredRecord record = store.create("confirmed_supervisor", caller.id().toString(), id.toString(),
                "CONFIRMED", assignment);
        audit.record(caller, "SUPERVISOR_ASSIGNED", IDEA, id);
        return new SupervisorResponse(record.getId(), teacherId, record.getStatus());
    }

    private StoredRecord findIdea(UUID id) {
        return store.repository().findById(id).filter(record -> IDEA.equals(record.getType()))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Idea was not found."));
    }
}
