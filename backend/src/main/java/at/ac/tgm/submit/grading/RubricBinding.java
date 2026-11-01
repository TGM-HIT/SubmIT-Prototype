package at.ac.tgm.submit.grading;

import static at.ac.tgm.submit.grading.GradingKeys.ASSESSMENT_TYPE;
import static at.ac.tgm.submit.grading.GradingKeys.BINDING;
import static at.ac.tgm.submit.grading.GradingKeys.FINAL_GRADE;
import static at.ac.tgm.submit.grading.GradingKeys.GROUP_GRADE;
import static at.ac.tgm.submit.grading.GradingKeys.METRIC;
import static at.ac.tgm.submit.grading.GradingKeys.PREVIOUS_RUBRIC_ID;
import static at.ac.tgm.submit.grading.GradingKeys.REASON;
import static at.ac.tgm.submit.grading.GradingKeys.RECORDED_AT;
import static at.ac.tgm.submit.grading.GradingKeys.RECORDED_BY;
import static at.ac.tgm.submit.grading.GradingKeys.RUBRIC_ID;
import static at.ac.tgm.submit.grading.GradingKeys.STUDENT_GRADE;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.audit.AuditService;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.security.AuthenticatedUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Tracks independent, immutable project rubric selection histories for each assessment phase. */
@Component
class RubricBinding {
    private static final int MAX_REASON_LENGTH = 2000;

    private final RecordStore store;
    private final RubricService rubrics;
    private final ProjectAccess access;
    private final AuditService audit;

    RubricBinding(RecordStore store, RubricService rubrics, ProjectAccess access, AuditService audit) {
        this.store = store;
        this.rubrics = rubrics;
        this.access = access;
        this.audit = audit;
    }

    RubricRef load(UUID rubricId) {
        return new RubricRef(rubricId, store.parse(rubrics.require(rubricId)));
    }

    UUID selected(UUID projectId, AssessmentType phase) {
        return latest(projectId, phase).map(record -> {
            JsonNode value = store.parse(record).get(RUBRIC_ID);
            return value == null || value.isNull() ? null : UUID.fromString(value.asText());
        }).orElse(null);
    }

    RubricRef requireSelected(UUID projectId, AssessmentType phase, JsonNode request) {
        UUID selected = selected(projectId, phase);
        if (selected == null) {
            throw GradingSupport.conflict("Select a " + phase.name().toLowerCase()
                    + " rubric for this project before recording assessments.");
        }
        UUID requested = GradingSupport.uuid(GradingSupport.text(request, RUBRIC_ID), RUBRIC_ID);
        RubricRef requestedRubric = load(requested);
        if (!selected.equals(requested)) {
            throw GradingSupport.conflict("The submitted rubric does not match the project's selected "
                    + phase.name().toLowerCase() + " rubric.");
        }
        return requestedRubric;
    }

    void set(AuthenticatedUser caller, UUID projectId, AssessmentType phase, UUID rubricId, String reason) {
        access.lockProject(projectId);
        UUID previous = selected(projectId, phase);
        if (Objects.equals(previous, rubricId)) {
            return;
        }
        boolean used = used(projectId, phase);
        validateSelection(rubricId, used);
        validateEffectiveness(rubricId);
        String cleanReason = transitionReason(reason, used && previous != null);
        saveSelection(caller, new BindingSelection(projectId, phase, rubricId, previous, cleanReason));
    }

    private void validateSelection(UUID rubricId, boolean used) {
        if (used && rubricId == null) {
            throw GradingSupport.conflict("A used phase cannot be cleared; select another rubric with a reason.");
        }
    }

    private void validateEffectiveness(UUID rubricId) {
        if (rubricId != null && !rubrics.isEffective(load(rubricId).payload(), LocalDate.now())) {
            throw GradingSupport.conflict("The rubric version is not effective today.");
        }
    }

    private String transitionReason(String reason, boolean required) {
        if (!required) {
            return null;
        }
        String cleanReason = reason == null ? null : reason.trim();
        if (cleanReason == null || cleanReason.isEmpty() || cleanReason.length() > MAX_REASON_LENGTH) {
            throw GradingSupport.bad("A non-blank transition reason of at most 2,000 characters is required.");
        }
        return cleanReason;
    }

    private void saveSelection(AuthenticatedUser caller, BindingSelection selection) {
        ObjectNode payload = store.object();
        payload.put("phase", selection.phase().name());
        if (selection.rubricId() == null) {
            payload.putNull(RUBRIC_ID);
        } else {
            payload.put(RUBRIC_ID, selection.rubricId().toString());
        }
        if (selection.previous() != null) {
            payload.put(PREVIOUS_RUBRIC_ID, selection.previous().toString());
        }
        if (selection.reason() != null) {
            payload.put(REASON, selection.reason());
        }
        payload.put(RECORDED_BY, caller.id().toString());
        payload.put(RECORDED_AT, Instant.now().toString());
        StoredRecord record = store.create(BINDING, caller.id().toString(), selection.projectId().toString(),
                selection.rubricId() == null ? "CLEARED" : "BOUND", payload);
        audit.record(caller, selection.previous() == null ? "ASSESSMENT_RUBRIC_SELECTED"
                : "ASSESSMENT_RUBRIC_TRANSITION",
                BINDING, record.getId());
    }

    private Optional<StoredRecord> latest(UUID projectId, AssessmentType phase) {
        return store.repository().findByTypeAndParentId(BINDING, projectId.toString()).stream()
                .sorted(Comparator.comparing(StoredRecord::getCreatedAt)
                        .thenComparing(StoredRecord::getId).reversed())
                .filter(record -> phase.name().equals(store.parse(record).path("phase").asText()))
                .findFirst();
    }

    private boolean used(UUID projectId, AssessmentType phase) {
        if (phase == AssessmentType.ROUTINE) {
            return hasRecords(GROUP_GRADE, projectId) || hasRecords(METRIC, projectId)
                    || hasStudentGrades(projectId, phase);
        }
        return hasRecords(FINAL_GRADE, projectId) || hasStudentGrades(projectId, phase);
    }

    private boolean hasRecords(String type, UUID projectId) {
        return !store.repository().findByTypeAndParentId(type, projectId.toString()).isEmpty();
    }

    private boolean hasStudentGrades(UUID projectId, AssessmentType phase) {
        return store.repository().findByTypeAndParentId(STUDENT_GRADE, projectId.toString()).stream()
                .anyMatch(record -> phase.name().equals(store.parse(record).path(ASSESSMENT_TYPE).asText()));
    }

    private record BindingSelection(UUID projectId, AssessmentType phase, UUID rubricId, UUID previous,
            String reason) {
    }
}
