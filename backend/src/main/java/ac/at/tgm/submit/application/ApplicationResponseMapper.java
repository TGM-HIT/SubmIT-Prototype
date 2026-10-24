package ac.at.tgm.submit.application;

import static java.util.HashMap.newHashMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.web.ApiDtos.ApplicationPeriodResponse;
import ac.at.tgm.submit.web.ApiDtos.CatalogueProjectResponse;
import ac.at.tgm.submit.web.ApiDtos.CatalogueSpecificationResponse;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import ac.at.tgm.submit.web.ApiDtos.PreferenceResponse;
import ac.at.tgm.submit.web.ApiDtos.ProjectTitleResponse;
import ac.at.tgm.submit.web.ApiDtos.StudentApplicationResponse;
import ac.at.tgm.submit.web.ApiDtos.StudentAssignmentResponse;
import ac.at.tgm.submit.web.ApiDtos.StudentGroupMemberResponse;
import java.time.Instant;
import java.util.ArrayList;
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
    private static final String FINALIZED = "FINALIZED";
    private static final String PROJECT_ID = "projectId";
    private static final String STUDENT_ID = "studentId";
    private static final String TITLE = "title";
    private static final String UNKNOWN_PROJECT = "Unknown project";

    private final RecordStore store;
    private final ApplicationPeopleMapper people;
    private final ApplyingGroupRecords groups;

    ApplicationResponseMapper(RecordStore store, ApplicationPeopleMapper people, ApplyingGroupRecords groups) {
        this.store = store;
        this.people = people;
        this.groups = groups;
    }

    ApplicationPeriodResponse period(StoredRecord record) {
        return period(record, false);
    }

    ApplicationPeriodResponse period(StoredRecord record, boolean studentView) {
        return period(record, studentView, null);
    }

    ApplicationPeriodResponse period(StoredRecord record, boolean studentView, UUID studentId) {
        return period(record, studentView, studentId, false);
    }

    ApplicationPeriodResponse period(StoredRecord record, boolean studentView, UUID studentId, boolean adminView) {
        ObjectNode data = store.parse(record);
        List<UUID> projects = uuidList(data.path(PROJECT_IDS));
        StoredRecord ownApplication = ownApplication(record, studentId);
        String schoolYear = data.path("schoolYear").asText();
        StudentAssignmentResponse studentAssignment = studentAssignment(record, studentId, ownApplication);
        String status = effectiveStatus(record, data);
        var eligibilityFields = people.periodEligibility(record, data, studentId, adminView, studentView);
        return new ApplicationPeriodResponse(record.getId(), periodLabel(data, schoolYear), schoolYear,
                Instant.parse(data.path(OPENS_AT).asText()), Instant.parse(data.path(CLOSES_AT).asText()),
                status, List.copyOf(projects), studentView ? projectTitles(record) : null,
                applicationStatus(studentId, ownApplication),
                studentAssignment,
                eligibilityFields.mode(),
                eligibilityFields.studentIds(),
                eligibilityFields.classIds(),
                eligibilityFields.eligibleToApply());
    }

    private StoredRecord ownApplication(StoredRecord period, UUID studentId) {
        if (studentId == null) {
            return null;
        }
        StoredRecord individual = store.repository()
                .findByTypeAndParentIdAndOwnerId(APPLICATION, period.getId().toString(), studentId.toString())
                .orElse(null);
        StoredRecord shared = groups.accepted(period.getId(), studentId.toString());
        return shared != null && "SUBMITTED".equals(shared.getStatus()) ? shared : individual;
    }

    private String periodLabel(JsonNode data, String schoolYear) {
        String label = data.path("label").asText(schoolYear);
        return label.isBlank() ? schoolYear : label;
    }

    private String applicationStatus(UUID studentId, StoredRecord application) {
        if (studentId == null) {
            return null;
        }
        if (application == null || "REPLACED".equals(application.getStatus())) {
            return "NOT_SUBMITTED";
        }
        return ApplyingGroupRecords.TYPE.equals(application.getType()) ? "SHARED_SUBMITTED" : application.getStatus();
    }

    StudentApplicationResponse sharedApplication(StoredRecord group, UUID caller) {
        List<PreferenceResponse> preferences = new ArrayList<>();
        for (String id : groups.ids(store.parse(group).path(PROJECT_IDS))) {
            preferences.add(new PreferenceResponse(UUID.fromString(id), preferences.size() + 1, projectTitle(id)));
        }
        return new StudentApplicationResponse(group.getId(), caller, displayName(caller.toString()),
                UUID.fromString(group.getParentId()), "SHARED_SUBMITTED", List.copyOf(preferences), null, null);
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
                UUID.fromString(record.getParentId()), record.getStatus(), List.copyOf(preferences), null, null);
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
        return periods(records, studentId, false);
    }

    PageResponse<ApplicationPeriodResponse> periods(Page<StoredRecord> records, UUID studentId, boolean adminView) {
        return new PageResponse<>(records.map(record -> period(record, studentId != null, studentId, adminView)).getContent(),
                records.getNumber(), records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    private List<UUID> uuidList(JsonNode node) {
        List<UUID> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(value -> values.add(UUID.fromString(value.asText())));
        }
        return List.copyOf(values);
    }

    Map<UUID, String> studentProjectTitles(StoredRecord period) {
        ApplicationPeriodResponse response = period(period, true);
        Map<UUID, String> titles = newHashMap(0);
        if (response.projectTitles() != null) {
            response.projectTitles().forEach(project -> titles.put(project.projectId(), project.title()));
        }
        return titles;
    }

    private List<ProjectTitleResponse> projectTitles(StoredRecord period) {
        ObjectNode payload = store.parse(period);
        Instant now = groups.now();
        Instant opensAt = Instant.parse(payload.path(OPENS_AT).asText());
        Instant closesAt = Instant.parse(payload.path(CLOSES_AT).asText());
        if (now.isBefore(opensAt) || now.isAfter(closesAt)) {
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

    private String effectiveStatus(StoredRecord record, ObjectNode data) {
        if (FINALIZED.equals(record.getStatus())) {
            return FINALIZED;
        }
        Instant now = groups.now();
        Instant opensAt = Instant.parse(data.path(OPENS_AT).asText());
        Instant closesAt = Instant.parse(data.path(CLOSES_AT).asText());
        if (now.isBefore(opensAt)) {
            return "DRAFT";
        }
        if (now.isBefore(closesAt)) {
            return "OPEN";
        }
        if ("ALLOCATED".equals(record.getStatus())) {
            return "ALLOCATED";
        }
        return "CLOSED";
    }

    PageResponse<StudentApplicationResponse> applications(Page<StoredRecord> records) {
        Map<UUID, String> names = newHashMap(0);
        records.getContent().stream().map(StoredRecord::getOwnerId).map(UUID::fromString).distinct()
                .forEach(id -> names.put(id, people.name(id)));
        List<UUID> projectIds = records.getContent().stream()
                .flatMap(record -> store.parse(record).path("preferences").findValuesAsText(PROJECT_ID).stream())
                .map(UUID::fromString).distinct().toList();
        Map<UUID, String> titles = newHashMap(0);
        store.repository().findAllById(projectIds).stream().filter(record -> "idea".equals(record.getType()))
                .forEach(record -> titles.put(record.getId(), store.parse(record).path(TITLE).asText()));
        List<StudentApplicationResponse> content = records.getContent().stream()
                .map(record -> staffApplication(record,
                        names.getOrDefault(UUID.fromString(record.getOwnerId()), "Unknown student"), titles))
                .toList();
        return new PageResponse<>(content, records.getNumber(), records.getSize(),
                records.getTotalElements(), records.getTotalPages());
    }

    private StudentApplicationResponse staffApplication(StoredRecord record, String displayName,
            Map<UUID, String> projectTitles) {
        return people.staffDetails(application(record, displayName, projectTitles));
    }

    private StudentAssignmentResponse studentAssignment(StoredRecord period, UUID studentId,
            StoredRecord application) {
        if (studentId == null) {
            return null;
        }
        String id = studentId.toString();
        if (application != null || hasPin(period, id) || includedInLatestEligibilitySnapshot(period, id)) {
            return assignment(period, id, application);
        }
        return null;
    }

    private boolean includedInLatestEligibilitySnapshot(StoredRecord period, String studentId) {
        boolean finalized = FINALIZED.equals(period.getStatus());
        StoredRecord run = latestRelevantRun(period, finalized);
        JsonNode eligible = run == null ? null : store.parse(run).path("eligibleStudentIds");
        if (eligible == null || !eligible.isArray()) {
            return false;
        }
        for (JsonNode id : eligible) {
            if (studentId.equals(id.asText())) {
                return true;
            }
        }
        return false;
    }

    private StudentAssignmentResponse assignment(StoredRecord period, String studentId, StoredRecord application) {
        if (application != null && !"SUBMITTED".equals(application.getStatus())) {
            if (!includedInLatestEligibilitySnapshot(period, studentId)) {
                return null;
            }
            // An inactive historical application is not evidence that its preferences
            // were used by this run. The immutable run result supplies the projection.
            application = null;
        }
        return assignmentFromRun(period, studentId, application);
    }

    private StudentAssignmentResponse assignmentFromRun(StoredRecord period, String studentId,
            StoredRecord application) {
        boolean finalized = FINALIZED.equals(period.getStatus());
        StoredRecord run = latestRelevantRun(period, finalized);
        if (run == null) {
            return new StudentAssignmentResponse("NO_RESULT", null, null, List.of(), null);
        }
        GroupAssignment group = finalized ? finalizedGroup(period, run, studentId)
                : preliminaryGroup(run, studentId);
        if (group.projectId() == null) {
            String state = finalized ? "NOT_ASSIGNED_FINAL" : "NOT_ASSIGNED_PRELIMINARY";
            return new StudentAssignmentResponse(state, null, null, List.of(), null);
        }
        String state = finalized ? "FINAL" : "PRELIMINARY";
        Boolean assignedWithoutPreferences = assignmentWithoutPreferences(run, studentId);
        return new StudentAssignmentResponse(state, projectTitle(group.projectId()),
                preferenceRank(application, group.projectId()),
                groupMembers(group.memberIds(), studentId, store.parse(run).get("applyingUnits")),
                assignedWithoutPreferences);
    }

    private Boolean assignmentWithoutPreferences(StoredRecord run, String studentId) {
        JsonNode assignments = store.parse(run).path("result").path("assignments");
        if (!assignments.isArray()) {
            return null;
        }
        for (JsonNode assignment : assignments) {
            if (studentId.equals(assignment.path(STUDENT_ID).asText()) && assignment.path("hadPreferences").isBoolean()) {
                return !assignment.path("hadPreferences").asBoolean();
            }
        }
        return null;
    }

    private StoredRecord latestRelevantRun(StoredRecord period, boolean finalized) {
        return store.repository().findByTypeAndParentIdOrderByCreatedAtDesc(RUN, period.getId().toString()).stream()
                .filter(candidate -> finalized ? FINALIZED.equals(candidate.getStatus())
                        : "COMPLETED".equals(candidate.getStatus())
                                && !store.parse(candidate).path("early").asBoolean(false))
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

    private List<StudentGroupMemberResponse> groupMembers(List<String> ids, String studentId, JsonNode units) {
        List<StudentGroupMemberResponse> members = new ArrayList<>();
        for (String id : ids) {
            UUID userId = parseUuid(id);
            String name = userId == null ? null : people.name(userId);
            String origin = originLabel(id, units);
            boolean own = origin.startsWith("Applying cohort") && origin.equals(originLabel(studentId, units));
            members.add(new StudentGroupMemberResponse(name == null || name.isBlank() ? "Unknown student" : name,
                    studentId.equals(id), origin, own));
        }
        members.sort(java.util.Comparator.comparing(StudentGroupMemberResponse::displayName,
                String.CASE_INSENSITIVE_ORDER).thenComparing(member -> !member.self()));
        return List.copyOf(members);
    }

    private String originLabel(String student, JsonNode units) {
        if (units == null || !units.isArray()) {
            return "Origin unavailable";
        }
        int cohort = 0;
        for (JsonNode unit : units) {
            cohort++;
            if (groups.ids(unit.path("studentIds")).contains(student)) {
                return "Applying cohort " + cohort;
            }
        }
        return "Applied individually · added by allocation";
    }

    private boolean hasPin(StoredRecord period, String studentId) {
        return store.repository().findByTypeAndParentIdAndOwnerId("allocation_pin", period.getId().toString(), studentId).isPresent();
    }

    private Integer preferenceRank(StoredRecord application, String projectId) {
        if (application == null || projectId == null) {
            return null;
        }
        if (ApplyingGroupRecords.TYPE.equals(application.getType())) {
            int index = groups.ids(store.parse(application).path(PROJECT_IDS)).indexOf(projectId);
            return index < 0 ? null : index + 1;
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
        return people.name(UUID.fromString(ownerId));
    }

}
