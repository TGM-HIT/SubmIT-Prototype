package at.ac.tgm.submit.formal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.web.ApiDtos.EligibleFormalProjectResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class EligibleFormalProjectMapper {
    private static final String PROJECT_GROUP = "project_group";
    private static final String PERIOD = "application_period";
    private static final String RUN = "allocation_run";
    private static final String APPLICATION = "formal_application";
    private static final String IDEA = "idea";
    private static final String FINALIZED = "FINALIZED";
    private static final String PROJECT_IDS = "projectIds";
    private static final String UNKNOWN_PROJECT = "Unknown project";

    private final RecordStore store;

    EligibleFormalProjectMapper(RecordStore store) {
        this.store = store;
    }

    EligibleFormalProjectResponse map(StoredRecord group, String studentId) {
        ObjectNode groupData = store.parse(group);
        if (!containsString(groupData.path("studentIds"), studentId)) {
            return null;
        }

        UUID projectId = parseUuid(group.getParentId());
        UUID periodId = parseUuid(groupData.path("periodId").asText());
        UUID runId = parseUuid(groupData.path("allocationRunId").asText());
        if (!validGroupReference(groupData, projectId, periodId, runId)) {
            return null;
        }

        StoredRecord period = finalizedPeriod(periodId);
        if (period == null || !containsString(store.parse(period).path(PROJECT_IDS), projectId.toString())
                || !finalizedRun(runId, periodId)) {
            return null;
        }
        return response(projectId, periodId, period);
    }

    private boolean validGroupReference(JsonNode groupData, UUID projectId, UUID periodId, UUID runId) {
        return projectId != null && periodId != null && runId != null
                && projectId.toString().equals(groupData.path("projectId").asText());
    }

    private StoredRecord finalizedPeriod(UUID periodId) {
        return store.repository().findById(periodId).filter(record -> PERIOD.equals(record.getType()))
                .filter(record -> FINALIZED.equals(record.getStatus())).orElse(null);
    }

    private boolean finalizedRun(UUID runId, UUID periodId) {
        return store.repository().findById(runId).filter(record -> RUN.equals(record.getType()))
                .filter(record -> periodId.toString().equals(record.getParentId()))
                .filter(record -> FINALIZED.equals(record.getStatus())).isPresent();
    }

    private EligibleFormalProjectResponse response(UUID projectId, UUID periodId, StoredRecord period) {
        ObjectNode periodData = store.parse(period);
        String schoolYear = nonBlank(periodData.path("schoolYear").asText(), "Unknown school year");
        String periodLabel = nonBlank(periodData.path("label").asText(), schoolYear);
        StoredRecord project = store.repository().findById(projectId)
                .filter(record -> IDEA.equals(record.getType())).orElse(null);
        String projectTitle = project == null ? UNKNOWN_PROJECT
                : nonBlank(store.parse(project).path("title").asText(), UNKNOWN_PROJECT);
        StoredRecord proposal = store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                APPLICATION, projectId.toString()).orElse(null);
        return new EligibleFormalProjectResponse(projectId, projectTitle, periodId, periodLabel, schoolYear,
                proposal == null ? null : proposal.getId(), proposal == null ? null : proposal.getStatus());
    }

    private boolean containsString(JsonNode values, String expected) {
        if (!values.isArray()) {
            return false;
        }
        Set<String> candidates = new HashSet<>();
        values.forEach(value -> {
            if (value.isTextual()) {
                candidates.add(value.textValue());
            }
        });
        return candidates.contains(expected);
    }

    private String nonBlank(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
