package com.flightlog.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.user.UserAccount;
import com.flightlog.user.UserAccountRepository;
import com.flightlog.web.ApiDtos.ApplicationPeriodResponse;
import com.flightlog.web.ApiDtos.CatalogueProjectResponse;
import com.flightlog.web.ApiDtos.CatalogueSpecificationResponse;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiDtos.PreferenceResponse;
import com.flightlog.web.ApiDtos.ProjectTitleResponse;
import com.flightlog.web.ApiDtos.StudentApplicationResponse;
import com.flightlog.web.ApiDtos.StudentAssignmentResponse;
import com.flightlog.web.ApiDtos.StudentGroupMemberResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
class ApplicationResponseMapper {
    private static final String PROJECT_IDS = "projectIds";
    private static final String OPENS_AT = "opensAt";
    private static final String CLOSES_AT = "closesAt";
    private static final String RUN = "allocation_run";
    private static final String GROUP = "project_group";
    private static final String APPLICATION = "student_application";
    private static final String PROJECT_ID = "projectId";
    private static final String STUDENT_ID = "studentId";
    private static final String TITLE = "title";
    private static final String UNKNOWN_PROJECT = "Unknown project";

    private final RecordStore store;
    private final UserAccountRepository users;

    ApplicationResponseMapper(RecordStore store, UserAccountRepository users) {
        this.store = store;
        this.users = users;
    }

    ApplicationPeriodResponse period(StoredRecord record) {
        return period(record, false);
    }

    ApplicationPeriodResponse period(StoredRecord record, boolean studentView) {
        return period(record, studentView, null);
    }

    ApplicationPeriodResponse period(StoredRecord record, boolean studentView, UUID studentId) {
        ObjectNode data = store.parse(record);
        List<UUID> projects = new ArrayList<>();
        data.path(PROJECT_IDS).forEach(project -> projects.add(UUID.fromString(project.asText())));
        StoredRecord ownApplication = studentId == null ? null : store.repository()
                .findByTypeAndParentIdAndOwnerId(APPLICATION, record.getId().toString(), studentId.toString())
                .orElse(null);
        String schoolYear = data.path("schoolYear").asText();
        String label = data.path("label").asText(schoolYear);
        if (label.isBlank()) {
            label = schoolYear;
        }
        return new ApplicationPeriodResponse(record.getId(), label, schoolYear,
                Instant.parse(data.path(OPENS_AT).asText()), Instant.parse(data.path(CLOSES_AT).asText()),
                record.getStatus(), List.copyOf(projects), studentView ? projectTitles(record) : null,
                studentId == null ? null : ownApplication == null ? "NOT_SUBMITTED" : ownApplication.getStatus(),
                ownApplication == null ? null : assignment(record, ownApplication));
    }

    StudentApplicationResponse studentApplication(StoredRecord record, Map<UUID, String> projectTitles) {
        return application(record, displayName(record.getOwnerId()), projectTitles);
    }

    private StudentApplicationResponse application(StoredRecord record, String displayName,
            Map<UUID, String> projectTitles) {
        ObjectNode payload = store.parse(record);
        List<PreferenceResponse> preferences = new ArrayList<>();
        payload.path("preferences").forEach(preference -> {
            UUID projectId = UUID.fromString(preference.path(PROJECT_ID).asText());
            String title = projectTitles.getOrDefault(projectId, UNKNOWN_PROJECT);
            preferences.add(new PreferenceResponse(projectId, preference.path("rank").asInt(),
                    title.isBlank() ? UNKNOWN_PROJECT : title));
        });
        return new StudentApplicationResponse(record.getId(), UUID.fromString(record.getOwnerId()), displayName,
                UUID.fromString(record.getParentId()), record.getStatus(), List.copyOf(preferences));
    }

    CatalogueProjectResponse catalogueProject(StoredRecord idea, StoredRecord specification) {
        ObjectNode payload = store.parse(idea);
        return new CatalogueProjectResponse(
                idea.getId(),
                payload.path(TITLE).asText(),
                payload.path("description").asText(),
                payload.path("intendedOutcome").asText(),
                strings(payload, "scopePoints"),
                strings(payload, "learningTopics"),
                payload.path("schoolYear").asText(),
                specification == null ? null : specificationResponse(specification));
    }

    PageResponse<ApplicationPeriodResponse> periods(Page<StoredRecord> records, UUID studentId) {
        return new PageResponse<>(records.map(record -> period(record, studentId != null, studentId)).getContent(),
                records.getNumber(), records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    Map<UUID, String> studentProjectTitles(StoredRecord period) {
        ApplicationPeriodResponse response = period(period, true);
        Map<UUID, String> titles = new HashMap<>();
        if (response.projectTitles() != null) {
            response.projectTitles().forEach(project -> titles.put(project.projectId(), project.title()));
        }
        return titles;
    }

    private List<ProjectTitleResponse> projectTitles(StoredRecord period) {
        ObjectNode payload = store.parse(period);
        Instant now = Instant.now();
        Instant opensAt = Instant.parse(payload.path(OPENS_AT).asText());
        Instant closesAt = Instant.parse(payload.path(CLOSES_AT).asText());
        if (!"OPEN".equals(period.getStatus()) || now.isBefore(opensAt) || now.isAfter(closesAt)) {
            return null;
        }
        List<ProjectTitleResponse> titles = new ArrayList<>();
        payload.path(PROJECT_IDS).forEach(project -> {
            UUID projectId = UUID.fromString(project.asText());
            String title = store.repository().findById(projectId)
                    .filter(record -> "idea".equals(record.getType()))
                    .filter(record -> "ACCEPTED_FOR_APPLICATIONS".equals(record.getStatus()))
                    .map(record -> store.parse(record).path(TITLE).asText())
                    .filter(value -> !value.isBlank())
                    .orElse(UNKNOWN_PROJECT);
            titles.add(new ProjectTitleResponse(projectId, title));
        });
        return List.copyOf(titles);
    }

    PageResponse<StudentApplicationResponse> applications(Page<StoredRecord> records) {
        Map<UUID, String> names = new HashMap<>();
        records.getContent().stream().map(StoredRecord::getOwnerId).map(UUID::fromString).distinct()
                .forEach(id -> users.findById(id).ifPresent(user -> names.put(id, user.getDisplayName())));
        List<UUID> projectIds = records.getContent().stream()
                .flatMap(record -> store.parse(record).path("preferences").findValuesAsText(PROJECT_ID).stream())
                .map(UUID::fromString).distinct().toList();
        Map<UUID, String> titles = new HashMap<>();
        store.repository().findAllById(projectIds).stream().filter(record -> "idea".equals(record.getType()))
                .forEach(record -> titles.put(record.getId(), store.parse(record).path(TITLE).asText()));
        List<StudentApplicationResponse> content = records.getContent().stream()
                .map(record -> application(record,
                        names.getOrDefault(UUID.fromString(record.getOwnerId()), "Unknown student"), titles))
                .toList();
        return new PageResponse<>(content, records.getNumber(), records.getSize(),
                records.getTotalElements(), records.getTotalPages());
    }

    private StudentAssignmentResponse assignment(StoredRecord period, StoredRecord application) {
        if (!"SUBMITTED".equals(application.getStatus())) {
            return null;
        }
        boolean finalized = "FINALIZED".equals(period.getStatus());
        StoredRecord run = latestRelevantRun(period, finalized);
        if (run == null) {
            return new StudentAssignmentResponse("NO_RESULT", null, null, List.of());
        }
        GroupAssignment group = finalized ? finalizedGroup(period, run, application.getOwnerId())
                : preliminaryGroup(run, application.getOwnerId());
        if (group.projectId() == null) {
            String state = finalized ? "NOT_ASSIGNED_FINAL" : "NOT_ASSIGNED_PRELIMINARY";
            return new StudentAssignmentResponse(state, null, null, List.of());
        }
        String state = finalized ? "FINAL" : "PRELIMINARY";
        return new StudentAssignmentResponse(state, projectTitle(group.projectId()),
                preferenceRank(application, group.projectId()), groupMembers(group.memberIds(), application.getOwnerId()));
    }

    private StoredRecord latestRelevantRun(StoredRecord period, boolean finalized) {
        return store.repository().findByTypeAndParentIdOrderByCreatedAtDesc(RUN, period.getId().toString()).stream()
                .filter(candidate -> finalized ? "FINALIZED".equals(candidate.getStatus())
                        : "COMPLETED".equals(candidate.getStatus()))
                .findFirst().orElse(null);
    }

    private GroupAssignment finalizedGroup(StoredRecord period, StoredRecord run, String studentId) {
        JsonNode projectIds = store.parse(period).path(PROJECT_IDS);
        for (JsonNode project : projectIds) {
            String projectId = project.asText();
            for (StoredRecord group : store.repository().findByTypeAndParentId(GROUP, projectId)) {
                ObjectNode payload = store.parse(group);
                if (period.getId().toString().equals(payload.path("periodId").asText())
                        && run.getId().toString().equals(payload.path("allocationRunId").asText())) {
                    List<String> ids = new ArrayList<>();
                    payload.path("studentIds").forEach(id -> ids.add(id.asText()));
                    if (ids.contains(studentId)) {
                        return new GroupAssignment(projectId, ids);
                    }
                }
            }
        }
        return new GroupAssignment(null, List.of());
    }

    private GroupAssignment preliminaryGroup(StoredRecord run, String studentId) {
        JsonNode assignments = store.parse(run).path("result").path("assignments");
        String projectId = null;
        for (JsonNode item : assignments) {
            if (studentId.equals(item.path(STUDENT_ID).asText())) {
                projectId = item.path(PROJECT_ID).asText();
                break;
            }
        }
        if (projectId == null) {
            return new GroupAssignment(null, List.of());
        }
        List<String> members = new ArrayList<>();
        for (JsonNode item : assignments) {
            if (projectId.equals(item.path(PROJECT_ID).asText())) {
                members.add(item.path(STUDENT_ID).asText());
            }
        }
        return new GroupAssignment(projectId, members);
    }

    private String projectTitle(String projectId) {
        UUID resolvedId = parseUuid(projectId);
        if (resolvedId == null) {
            return UNKNOWN_PROJECT;
        }
        return store.repository().findById(resolvedId).filter(record -> "idea".equals(record.getType()))
                .map(record -> store.parse(record).path(TITLE).asText())
                .filter(value -> !value.isBlank()).orElse(UNKNOWN_PROJECT);
    }

    private List<StudentGroupMemberResponse> groupMembers(List<String> ids, String studentId) {
        List<StudentGroupMemberResponse> members = new ArrayList<>();
        for (String id : ids) {
            UUID userId = parseUuid(id);
            String name = userId == null ? null : users.findById(userId)
                    .map(UserAccount::getDisplayName).orElse(null);
            members.add(new StudentGroupMemberResponse(name == null || name.isBlank() ? "Unknown student" : name,
                    studentId.equals(id)));
        }
        members.sort(java.util.Comparator.comparing(StudentGroupMemberResponse::displayName,
                String.CASE_INSENSITIVE_ORDER).thenComparing(member -> !member.self()));
        return List.copyOf(members);
    }

    private Integer preferenceRank(StoredRecord application, String projectId) {
        if (projectId == null) {
            return null;
        }
        for (JsonNode preference : store.parse(application).path("preferences")) {
            if (projectId.equals(preference.path(PROJECT_ID).asText())) {
                return preference.path("rank").asInt();
            }
        }
        return null;
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private CatalogueSpecificationResponse specificationResponse(StoredRecord record) {
        ObjectNode payload = store.parse(record);
        return new CatalogueSpecificationResponse(
                strings(payload, "objectives"), strings(payload, "intendedUsers"),
                strings(payload, "requirements"), strings(payload, "deliverables"),
                payload.path("scope").asText(), strings(payload, "dependencies"));
    }

    private List<String> strings(ObjectNode payload, String field) {
        List<String> values = new ArrayList<>();
        payload.path(field).forEach(node -> values.add(node.asText()));
        return List.copyOf(values);
    }

    private record GroupAssignment(String projectId, List<String> memberIds) {
    }

    private String displayName(String ownerId) {
        return users.findById(UUID.fromString(ownerId)).map(UserAccount::getDisplayName).orElse("Unknown student");
    }

}
