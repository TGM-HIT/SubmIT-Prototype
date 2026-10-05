package com.flightlog.grading;

import static com.flightlog.grading.GradingKeys.BINDING;
import static com.flightlog.grading.GradingKeys.PREVIOUS_RUBRIC_ID;
import static com.flightlog.grading.GradingKeys.REASON;
import static com.flightlog.grading.GradingKeys.RECORDED_AT;
import static com.flightlog.grading.GradingKeys.RECORDED_BY;
import static com.flightlog.grading.GradingKeys.RUBRIC_ID;
import static com.flightlog.grading.GradingKeys.TRANSITION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.audit.AuditService;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.security.AuthenticatedUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Tracks which rubric version a project is bound to. The first assessment binds the project; any later use of a
 * different version needs an explicit, recorded transition and never rewrites earlier assessments.
 */
@Component
class RubricBinding {
    private final RecordStore store;
    private final RubricService rubrics;
    private final AuditService audit;

    RubricBinding(RecordStore store, RubricService rubrics, AuditService audit) {
        this.store = store;
        this.rubrics = rubrics;
        this.audit = audit;
    }

    RubricRef load(UUID rubricId) {
        return new RubricRef(rubricId, store.parse(rubrics.require(rubricId)));
    }

    RubricRef bind(AuthenticatedUser caller, UUID projectId, JsonNode request) {
        UUID rubricId = GradingSupport.uuid(GradingSupport.text(request, RUBRIC_ID), RUBRIC_ID);
        RubricRef rubric = load(rubricId);
        Optional<StoredRecord> latest = store.repository()
                .findFirstByTypeAndParentIdOrderByCreatedAtDesc(BINDING, projectId.toString());
        String previous = latest.map(record -> store.parse(record).path(RUBRIC_ID).asText()).orElse(null);
        if (rubricId.toString().equals(previous)) {
            return rubric;
        }
        if (!rubrics.isEffective(rubric.payload(), LocalDate.now())) {
            throw GradingSupport.conflict("The rubric version is not effective today.");
        }
        String reason = previous == null ? "Initial binding" : transitionReason(request, previous);
        ObjectNode payload = store.object().put(RUBRIC_ID, rubricId.toString()).put(REASON, reason)
                .put(RECORDED_BY, caller.id().toString()).put(RECORDED_AT, Instant.now().toString());
        if (previous != null) {
            payload.put(PREVIOUS_RUBRIC_ID, previous);
        }
        StoredRecord record = store.create(BINDING, caller.id().toString(), projectId.toString(), "BOUND", payload);
        audit.record(caller, previous == null ? "RUBRIC_BOUND" : "RUBRIC_VERSION_TRANSITION", BINDING, record.getId());
        return rubric;
    }

    private String transitionReason(JsonNode request, String previous) {
        String reason = GradingSupport.optionalText(request.path(TRANSITION), REASON);
        if (reason == null) {
            throw GradingSupport.conflict("The project is bound to rubric version " + previous
                    + "; an explicit '" + TRANSITION + "' with a reason is required to use another version.");
        }
        return reason;
    }
}
