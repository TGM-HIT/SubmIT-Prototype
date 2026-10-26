package ac.at.tgm.submit.allocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.allocation.AllocationOptimizer.Application;
import ac.at.tgm.submit.allocation.AllocationOptimizer.Project;
import ac.at.tgm.submit.allocation.AllocationOptimizer.Request;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.application.ApplyingGroupRecords;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AllocationEngine {
    private static final String PROJECT_ID = "projectId";
    private static final String STUDENT_ID = "studentId";
    private static final String PIN_REASON = "PINNED_PROJECT_CANNOT_FORM_GROUP";
    private final RecordStore store;
    private final UserAccountRepository users;
    private final AllocationOptimizer optimizer = new AllocationOptimizer();
    private final ApplyingUnitSnapshots units;

    AllocationEngine(RecordStore store, UserAccountRepository users, ApplyingUnitSnapshots units) {
        this.store = store;
        this.users = users;
        this.units = units;
    }

    List<StoredRecord> submittedGroups(UUID periodId) {
        return units.submitted(periodId);
    }

    com.fasterxml.jackson.databind.node.ArrayNode serializeUnits(List<StoredRecord> groups) {
        return units.serialize(groups);
    }

    void persistOrigin(ObjectNode group, JsonNode run) {
        units.persistOrigin(group, run);
    }

    Object optimize(List<String> projectIds, List<StoredRecord> records, List<StoredRecord> pinRecords,
            List<String> eligibleStudentIds) {
        List<Project> projects = projectIds.stream().map(Project::new).toList();
        Map<String, String> pinnedProjects = readPins(projectIds, pinRecords);
        units.expandPins(records, pinnedProjects);
        Map<String, Integer> originalRanks = new HashMap<>();
        List<Application> applications = buildApplications(records, pinnedProjects, originalRanks, eligibleStudentIds);
        Request request = new Request(projects, applications, pinnedProjects.keySet());
        ObjectNode result = (ObjectNode) store.value(optimizer.optimize(request));
        Set<String> preferenceStudents = applications.stream().filter(Application::hasPreferences)
                .map(Application::studentId).collect(java.util.stream.Collectors.toSet());
        applyPinResults(result, pinnedProjects, originalRanks, preferenceStudents, projectIds.size());
        markPreferenceSubmission(result, applications);
        return result;
    }

    private void markPreferenceSubmission(ObjectNode result, List<Application> applications) {
        Map<String, Boolean> state = new HashMap<>();
        applications.forEach(application -> state.put(application.studentId(), application.hasPreferences()));
        result.path("assignments").forEach(assignment -> {
            if (assignment instanceof ObjectNode object) {
                object.put("hadPreferences", state.getOrDefault(object.path(STUDENT_ID).asText(), false));
            }
        });
    }

    private Map<String, String> readPins(List<String> projectIds, List<StoredRecord> records) {
        Map<String, String> pins = new HashMap<>();
        for (StoredRecord record : records) {
            String projectId = validPinProject(record, projectIds);
            if (projectId != null) {
                pins.put(record.getOwnerId(), projectId);
            }
        }
        return pins;
    }

    private String validPinProject(StoredRecord pin, List<String> projectIds) {
        UUID studentId;
        try {
            studentId = UUID.fromString(pin.getOwnerId());
        } catch (IllegalArgumentException exception) {
            return null;
        }
        if (users.findById(studentId).filter(user -> user.hasRole(Role.STUDENT)).isEmpty()) {
            return null;
        }
        String projectId = store.parse(pin).path(PROJECT_ID).asText();
        return projectIds.contains(projectId) ? projectId : null;
    }

    private List<Application> buildApplications(List<StoredRecord> records, Map<String, String> pins,
            Map<String, Integer> originalRanks, List<String> eligibleStudentIds) {
        List<Application> applications = new ArrayList<>();
        Set<String> included = new HashSet<>();
        for (StoredRecord record : records) {
            addRecord(record, pins, originalRanks, applications, included);
        }
        addPinOnlyStudents(pins, included, applications);
        addEligibleStudents(eligibleStudentIds, pins, included, applications);
        return applications;
    }

    private void addRecord(StoredRecord record, Map<String, String> pins, Map<String, Integer> originalRanks,
            List<Application> applications, Set<String> included) {
        if (ApplyingGroupRecords.TYPE.equals(record.getType())) {
            addShared(record, pins, originalRanks, applications);
            store.parse(record).path("members").forEach(member -> included.add(member.asText()));
        } else {
            addIndividual(record, pins, originalRanks, applications, included);
        }
    }

    private void addIndividual(StoredRecord record, Map<String, String> pins, Map<String, Integer> originalRanks,
            List<Application> applications, Set<String> included) {
        String studentId = record.getOwnerId();
        List<String> sourceRanking = rankedProjects(record);
        rememberRanks(studentId, record, originalRanks);
        List<String> ranking = pins.containsKey(studentId) ? List.of(pins.get(studentId)) : sourceRanking;
        if (!ranking.isEmpty()) {
            applications.add(new Application(studentId, ranking, null, !sourceRanking.isEmpty()));
            included.add(studentId);
        }
    }

    private void addPinOnlyStudents(Map<String, String> pins, Set<String> included,
            List<Application> applications) {
        for (Map.Entry<String, String> pin : pins.entrySet()) {
            if (!included.contains(pin.getKey())) {
                applications.add(new Application(pin.getKey(), List.of(pin.getValue()), null, false));
                included.add(pin.getKey());
            }
        }
    }

    private void addEligibleStudents(List<String> eligibleStudentIds, Map<String, String> pins,
            Set<String> included, List<Application> applications) {
        for (String studentId : eligibleStudentIds) {
            if (included.add(studentId)) {
                String pinned = pins.get(studentId);
                applications.add(new Application(studentId, pinned == null ? List.of() : List.of(pinned),
                        null, false));
            }
        }
    }

    private void addShared(StoredRecord group, Map<String, String> pins, Map<String, Integer> ranks,
            List<Application> applications) {
        List<String> ranking = new ArrayList<>();
        store.parse(group).path("projectIds").forEach(project -> ranking.add(project.asText()));
        for (JsonNode member : store.parse(group).path("members")) {
            String student = member.asText();
            for (int i = 0; i < ranking.size(); i++) {
                ranks.put(student + "|" + ranking.get(i), i + 1);
            }
            List<String> effective = pins.containsKey(student) ? List.of(pins.get(student)) : ranking;
            applications.add(new Application(student, effective, group.getId().toString()));
        }
    }

    private List<String> rankedProjects(StoredRecord record) {
        List<String> ranking = new ArrayList<>();
        store.parse(record).path("preferences").forEach(item -> ranking.add(item.path(PROJECT_ID).asText()));
        return ranking;
    }

    private void rememberRanks(String studentId, StoredRecord record, Map<String, Integer> ranks) {
        for (JsonNode preference : store.parse(record).path("preferences")) {
            String projectId = preference.path(PROJECT_ID).asText();
            int rank = preference.path("rank").asInt();
            if (rank > 0) {
                ranks.put(studentId + "|" + projectId, rank);
            }
        }
    }

    private void applyPinResults(ObjectNode result, Map<String, String> pins, Map<String, Integer> ranks,
            Set<String> preferenceStudents, int projectCount) {
        long score = result.path("preferenceScore").asLong();
        score += markPinnedAssignments(result.path("assignments"), pins, ranks, preferenceStudents, projectCount);
        result.put("preferenceScore", score);
        markPinnedUnassigned(result.path("unassignedStudents"), pins);
    }

    private long markPinnedAssignments(JsonNode assignments, Map<String, String> pins,
            Map<String, Integer> ranks, Set<String> preferenceStudents, int projectCount) {
        long scoreAdjustment = 0;
        for (JsonNode assignment : assignments) {
            if (assignment instanceof ObjectNode object) {
                String studentId = object.path(STUDENT_ID).asText();
                if (pins.containsKey(studentId)) {
                    String projectId = object.path(PROJECT_ID).asText();
                    int actualRank = ranks.getOrDefault(studentId + "|" + projectId, 0);
                    object.put("pinned", true);
                    object.put("preferenceRank", actualRank);
                    if (preferenceStudents.contains(studentId)) {
                        scoreAdjustment += (actualRank == 0 ? 0 : projectCount - actualRank + 1) - projectCount;
                    }
                }
            }
        }
        return scoreAdjustment;
    }

    private void markPinnedUnassigned(JsonNode students, Map<String, String> pins) {
        for (JsonNode student : students) {
            String studentId = student.path(STUDENT_ID).asText();
            if (student instanceof ObjectNode object && pins.containsKey(studentId)) {
                object.put("reason", PIN_REASON);
                object.put("explanation", "The administrator pin could not form a valid group of at least three students.");
            }
        }
    }
}
