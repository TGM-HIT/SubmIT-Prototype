package com.flightlog.allocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.allocation.AllocationOptimizer.Application;
import com.flightlog.allocation.AllocationOptimizer.Project;
import com.flightlog.allocation.AllocationOptimizer.Request;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.security.Role;
import com.flightlog.user.UserAccountRepository;
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

    AllocationEngine(RecordStore store, UserAccountRepository users) {
        this.store = store;
        this.users = users;
    }

    Object optimize(List<String> projectIds, List<StoredRecord> records, List<StoredRecord> pinRecords) {
        List<Project> projects = projectIds.stream().map(Project::new).toList();
        Map<String, String> pinnedProjects = readPins(projectIds, pinRecords);
        Map<String, Integer> originalRanks = new HashMap<>();
        List<Application> applications = buildApplications(records, pinnedProjects, originalRanks);
        Request request = new Request(projects, applications, pinnedProjects.keySet());
        ObjectNode result = (ObjectNode) store.value(optimizer.optimize(request));
        applyPinResults(result, pinnedProjects, originalRanks, projectIds.size());
        return result;
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
        if (users.findById(studentId).filter(user -> user.getRole() == Role.STUDENT).isEmpty()) {
            return null;
        }
        String projectId = store.parse(pin).path(PROJECT_ID).asText();
        return projectIds.contains(projectId) ? projectId : null;
    }

    private List<Application> buildApplications(List<StoredRecord> records, Map<String, String> pins,
            Map<String, Integer> originalRanks) {
        List<Application> applications = new ArrayList<>();
        Set<String> included = new HashSet<>();
        for (StoredRecord record : records) {
            String studentId = record.getOwnerId();
            List<String> sourceRanking = rankedProjects(record);
            rememberRanks(studentId, record, originalRanks);
            List<String> ranking = pins.containsKey(studentId) ? List.of(pins.get(studentId)) : sourceRanking;
            if (!ranking.isEmpty()) {
                applications.add(new Application(studentId, ranking));
                included.add(studentId);
            }
        }
        for (Map.Entry<String, String> pin : pins.entrySet()) {
            if (!included.contains(pin.getKey())) {
                applications.add(new Application(pin.getKey(), List.of(pin.getValue())));
            }
        }
        return applications;
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
            int projectCount) {
        long score = result.path("preferenceScore").asLong();
        score += markPinnedAssignments(result.path("assignments"), pins, ranks, projectCount);
        result.put("preferenceScore", score);
        markPinnedUnassigned(result.path("unassignedStudents"), pins);
    }

    private long markPinnedAssignments(JsonNode assignments, Map<String, String> pins,
            Map<String, Integer> ranks, int projectCount) {
        long scoreAdjustment = 0;
        for (JsonNode assignment : assignments) {
            if (assignment instanceof ObjectNode object) {
                String studentId = object.path(STUDENT_ID).asText();
                if (pins.containsKey(studentId)) {
                    String projectId = object.path(PROJECT_ID).asText();
                    int actualRank = ranks.getOrDefault(studentId + "|" + projectId, 0);
                    object.put("pinned", true);
                    object.put("preferenceRank", actualRank);
                    scoreAdjustment += (actualRank == 0 ? 0 : projectCount - actualRank + 1) - projectCount;
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
