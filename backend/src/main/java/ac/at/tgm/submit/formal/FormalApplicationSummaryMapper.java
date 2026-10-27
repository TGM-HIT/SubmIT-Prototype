package ac.at.tgm.submit.formal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.web.ApiDtos.FormalApplicationSummaryResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class FormalApplicationSummaryMapper {
    private static final String GROUP = "project_group";
    private static final String IDEA = "idea";
    private static final String PERIOD = "application_period";
    private static final String CONFIRMED_SUPERVISOR = "confirmed_supervisor";
    private static final String UNKNOWN_PROJECT = "Unknown project";
    private static final String UNKNOWN_STUDENT = "Unknown student";
    private static final String UNKNOWN_PERIOD = "Unknown period";
    private static final String UNKNOWN_SCHOOL_YEAR = "Unknown school year";

    private final RecordStore store;
    private final UserAccountRepository users;

    FormalApplicationSummaryMapper(RecordStore store, UserAccountRepository users) {
        this.store = store;
        this.users = users;
    }

    FormalApplicationSummaryResponse summary(StoredRecord application) {
        UUID projectId = parseUuid(application.getParentId());
        StoredRecord project = projectId == null ? null : store.repository().findById(projectId)
                .filter(record -> IDEA.equals(record.getType())).orElse(null);
        String title = project == null ? UNKNOWN_PROJECT
                : nonBlank(store.parse(project).path("title").asText(), UNKNOWN_PROJECT);

        StoredRecord group = projectId == null ? null : store.repository()
                .findFirstByTypeAndParentIdOrderByCreatedAtDesc(GROUP, projectId.toString()).orElse(null);
        List<String> names = studentNames(group);
        StoredRecord period = linkedPeriod(group);
        String schoolYear = period == null ? UNKNOWN_SCHOOL_YEAR
                : nonBlank(store.parse(period).path("schoolYear").asText(), UNKNOWN_SCHOOL_YEAR);
        String periodLabel = period == null ? UNKNOWN_PERIOD
                : nonBlank(store.parse(period).path("label").asText(), schoolYear);
        return new FormalApplicationSummaryResponse(application.getId(), projectId, title, names, periodLabel,
                schoolYear, application.getStatus());
    }

    boolean isConfirmedSupervisor(StoredRecord application, String teacherId) {
        return store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                        CONFIRMED_SUPERVISOR, application.getParentId())
                .filter(assignment -> "CONFIRMED".equals(assignment.getStatus()))
                .map(store::parse)
                .map(data -> teacherId.equals(data.path("teacherId").asText()))
                .orElse(false);
    }

    private List<String> studentNames(StoredRecord group) {
        if (group == null) {
            return List.of();
        }
        JsonNode ids = store.parse(group).path("studentIds");
        if (!ids.isArray()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode studentId : ids) {
            UserAccount account = parseUuid(studentId.asText()) == null ? null
                    : users.findById(parseUuid(studentId.asText())).orElse(null);
            names.add(account == null ? UNKNOWN_STUDENT : nonBlank(account.getDisplayName(), UNKNOWN_STUDENT));
        }
        return List.copyOf(names);
    }

    private StoredRecord linkedPeriod(StoredRecord group) {
        if (group == null) {
            return null;
        }
        ObjectNode data = store.parse(group);
        UUID periodId = parseUuid(data.path("periodId").asText());
        return periodId == null ? null : store.repository().findById(periodId)
                .filter(record -> PERIOD.equals(record.getType())).orElse(null);
    }

    private String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
