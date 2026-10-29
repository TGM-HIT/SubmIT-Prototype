package ac.at.tgm.submit.allocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.audit.AuditService;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import ac.at.tgm.submit.web.ApiException;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AllocationService {
    private static final String PERIOD = "application_period";
    private static final String RUN = "allocation_run";
    private static final String APPLICATION = "student_application";
    private static final String PROJECT_GROUP = "project_group";
    private static final String FINALIZED = "FINALIZED";
    private static final String RESULT_FIELD = "result";
    private static final String EARLY_FIELD = "early";
    private static final String ASSIGNMENTS_FIELD = "assignments";
    private static final String PROJECT_ID_FIELD = "projectId";
    private static final String STUDENT_ID_FIELD = "studentId";
    private static final String UNASSIGNED_STUDENTS_FIELD = "unassignedStudents";
    private static final String UNASSIGNED_PROJECTS_FIELD = "unassignedProjects";
    private static final int APPLICATION_PAGE_SIZE = 100;

    private final RecordStore store;
    private final AuditService audit;
    private final AllocationEngine engine;
    private final AllocationRuntime runtime;

    public AllocationService(RecordStore store, AuditService audit, AllocationEngine engine,
            AllocationRuntime runtime) {
        this.store = store;
        this.audit = audit;
        this.engine = engine;
        this.runtime = runtime;
    }

    @Transactional
    public int startDuePeriods() {
        int started = 0;
        int pageNumber = 0;
        Page<StoredRecord> periods;
        do {
            periods = store.page(PERIOD, null, null,
                    PageRequest.of(pageNumber++, 100, Sort.by(Sort.Direction.ASC, "id")));
            for (StoredRecord period : periods.getContent()) {
                StoredRecord current = store.lock(PERIOD, period.getId());
                if (ensureInitialRunIfDue(current)) {
                    started++;
                }
            }
        } while (periods.hasNext());
        return started;
    }

    @Transactional
    public boolean ensureInitialRunIfDue(StoredRecord period) {
        if (!isDue(period) || FINALIZED.equals(period.getStatus()) || hasInitialRun(period.getId())) {
            return false;
        }
        createRun(null, period, true, false);
        return true;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ObjectNode startReplacement(AuthenticatedUser caller, UUID periodId) {
        StoredRecord period = store.lock(PERIOD, periodId);
        if (FINALIZED.equals(period.getStatus())) {
            throw conflict("A finalized allocation cannot be replaced.");
        }
        ObjectNode periodData = store.parse(period);
        Instant now = runtime.now();
        Instant closesAt = Instant.parse(periodData.path("closesAt").asText());
        boolean beforeDeadline = now.isBefore(closesAt);
        boolean beforeOpening = now.isBefore(Instant.parse(periodData.path("opensAt").asText()));
        boolean early = beforeDeadline;
        if (now.equals(closesAt)) {
            throw conflict("An allocation run cannot start at the exact deadline.");
        }
        if (early && beforeOpening) {
            throw conflict("An allocation run can start early only while the application period is OPEN.");
        }
        if (!early) {
            ensureInitialRunIfDue(period);
            store.update(period, "CLOSED", store.parse(period));
        }
        StoredRecord run = createRun(caller, period, false, early);
        return response(run);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public PageResponse<ObjectNode> listRuns(AuthenticatedUser caller, UUID periodId, int page, int size) {
        findPeriod(periodId);
        return pageResponse(store.page(RUN, null, periodId.toString(), PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ObjectNode getRun(AuthenticatedUser caller, UUID id) {
        return response(findRun(id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ObjectNode finalizeRun(AuthenticatedUser caller, UUID id) {
        StoredRecord run = findRun(id);
        if (store.parse(run).path(EARLY_FIELD).asBoolean(false)) {
            throw conflict("An early allocation run cannot be finalized.");
        }
        if (!"COMPLETED".equals(run.getStatus())) {
            throw conflict("Only a completed allocation can be finalized.");
        }
        StoredRecord period = store.lock(PERIOD, UUID.fromString(run.getParentId()));
        if (FINALIZED.equals(period.getStatus())) {
            throw conflict("This application period is already finalized.");
        }
        ensureInitialRunIfDue(period);
        ObjectNode payload = store.parse(run);
        JsonNode assignments = payload.path(RESULT_FIELD).path(ASSIGNMENTS_FIELD);
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (JsonNode assignment : assignments) {
            groups.computeIfAbsent(assignment.path(PROJECT_ID_FIELD).asText(), ignored -> new ArrayList<>())
                    .add(assignment.path(STUDENT_ID_FIELD).asText());
        }
        for (Map.Entry<String, List<String>> group : groups.entrySet()) {
            ObjectNode groupPayload = store.object().put(PROJECT_ID_FIELD, group.getKey())
                    .put("periodId", period.getId().toString()).put("allocationRunId", id.toString());
            ArrayNode students = groupPayload.putArray("studentIds");
            group.getValue().forEach(students::add);
            engine.persistOrigin(groupPayload, payload);
            store.create(PROJECT_GROUP, caller.id().toString(), group.getKey(), "ALLOCATED", groupPayload);
        }
        payload.put("finalizedAt", runtime.now().toString());
        store.update(run, FINALIZED, payload);
        store.update(period, FINALIZED, store.parse(period));
        audit.record(caller, "ALLOCATION_FINALIZED", RUN, id);
        return response(run);
    }

    private StoredRecord createRun(AuthenticatedUser caller, StoredRecord period, boolean initial, boolean early) {
        ObjectNode periodData = store.parse(period);
        List<String> projectIds = readIds(periodData.path("projectIds"), "projectIds");
        List<String> eligibleIds = runtime.eligibleStudents(period).stream()
                .map(user -> user.getId().toString()).toList();
        Set<String> eligible = Set.copyOf(eligibleIds);
        List<StoredRecord> allShared = engine.submittedGroups(period.getId());
        List<StoredRecord> eligibleGroups = selectSharedGroups(allShared, eligible);
        Set<String> excludedMembers = excludedGroupMembers(allShared, eligibleGroups);
        List<String> candidateIds = eligibleIds.stream()
                .filter(id -> !excludedMembers.contains(id)).toList();
        List<StoredRecord> pins = eligiblePins(period.getId(), eligible, excludedMembers);
        ObjectNode snapshot = snapshot(projectIds, List.of(), pins);
        snapshot.set("eligibleStudentIds", store.value(eligibleIds));
        snapshot.put("initial", initial);
        snapshot.put(EARLY_FIELD, early);
        snapshot.put("createdAt", runtime.now().toString());
        StoredRecord run = store.create(RUN, caller == null ? null : caller.id().toString(),
                period.getId().toString(), "RUNNING", snapshot);
        try {
            List<StoredRecord> applications = readApplications(period.getId()).stream()
                    .filter(application -> eligible.contains(application.getOwnerId()))
                    .filter(application -> !excludedMembers.contains(application.getOwnerId())).toList();
            snapshot.set("applications", serializeApplications(applications));
            snapshot.set("excludedApplyingUnits", excludedApplyingUnits(allShared, eligibleGroups));
            snapshot.set("applyingUnits", engine.serializeUnits(eligibleGroups));
            applications = new ArrayList<>(applications);
            applications.addAll(eligibleGroups);
            Object result = engine.optimize(projectIds, applications, pins, candidateIds);
            snapshot.set(RESULT_FIELD, store.value(result));
            store.update(run, "COMPLETED", snapshot);
            if (!early) {
                store.update(period, "ALLOCATED", periodData);
            }
        } catch (IllegalArgumentException exception) {
            snapshot.put("error", exception.getMessage());
            snapshot.put("inputSnapshotComplete", false);
            store.update(run, "FAILED", snapshot);
            if (!early) {
                store.update(period, "CLOSED", periodData);
            }
        }
        if (caller != null) {
            audit.record(caller, "ALLOCATION_RUN_STARTED", RUN, run.getId());
        } else {
            audit.recordSystem("ALLOCATION_RUN_STARTED", RUN, run.getId());
        }
        return run;
    }

    private boolean allMembersEligible(StoredRecord group, Set<String> eligible) {
        JsonNode members = store.parse(group).path("members");
        if (!members.isArray()) {
            return false;
        }
        for (JsonNode member : members) {
            if (!eligible.contains(member.asText())) {
                return false;
            }
        }
        return true;
    }

    private List<StoredRecord> selectSharedGroups(List<StoredRecord> groups, Set<String> eligible) {
        return groups.stream().filter(group -> allMembersEligible(group, eligible)).toList();
    }

    private Set<String> excludedGroupMembers(List<StoredRecord> all, List<StoredRecord> included) {
        Set<UUID> includedIds = included.stream().map(StoredRecord::getId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> excluded = new HashSet<>();
        for (StoredRecord group : all) {
            if (!includedIds.contains(group.getId())) {
                store.parse(group).path("members").forEach(member -> excluded.add(member.asText()));
            }
        }
        return Set.copyOf(excluded);
    }

    private List<StoredRecord> eligiblePins(UUID periodId, Set<String> eligible, Set<String> excludedMembers) {
        return readPins(periodId).stream()
                .filter(pin -> eligible.contains(pin.getOwnerId()))
                .filter(pin -> !excludedMembers.contains(pin.getOwnerId())).toList();
    }

    private ArrayNode excludedApplyingUnits(List<StoredRecord> all, List<StoredRecord> included) {
        Set<UUID> includedIds = included.stream().map(StoredRecord::getId)
                .collect(java.util.stream.Collectors.toSet());
        ArrayNode excluded = store.object().arrayNode();
        for (StoredRecord group : all) {
            if (includedIds.contains(group.getId())) {
                continue;
            }
            List<String> memberIds = new ArrayList<>();
            store.parse(group).path("members").forEach(member -> memberIds.add(member.asText()));
            List<UUID> ids = memberIds.stream().map(UUID::fromString).toList();
            Map<UUID, String> displayNames = runtime.displayNames(ids);
            ObjectNode entry = excluded.addObject().put("groupId", group.getId().toString())
                    .put("reason", "MEMBER_INELIGIBLE");
            ArrayNode savedIds = entry.putArray("memberIds");
            ArrayNode savedNames = entry.putArray("memberDisplayNames");
            for (UUID id : ids) {
                savedIds.add(id.toString());
                savedNames.add(displayNames.getOrDefault(id, "Unknown student"));
            }
        }
        return excluded;
    }

    private List<StoredRecord> readApplications(UUID periodId) {
        List<StoredRecord> applications = new ArrayList<>();
        StoredRecord cursor = null;
        Page<StoredRecord> page;
        do {
            var ordering = PageRequest.of(0, APPLICATION_PAGE_SIZE,
                    Sort.by(Sort.Direction.ASC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
            page = cursor == null
                    ? store.page(APPLICATION, null, periodId.toString(), ordering)
                    : store.repository().findByTypeAndParentIdAfterCreationCursor(APPLICATION, periodId.toString(),
                            cursor.getCreatedAt(), cursor.getId(), ordering);
            applications.addAll(page.getContent());
            if (!page.getContent().isEmpty()) {
                cursor = page.getContent().getLast();
            }
        } while (page.getContent().size() == APPLICATION_PAGE_SIZE);
        return applications.stream().filter(application -> "SUBMITTED".equals(application.getStatus())).toList();
    }

    private ObjectNode snapshot(List<String> projects, List<StoredRecord> applications, List<StoredRecord> pins) {
        ObjectNode snapshot = store.object();
        ArrayNode projectIds = snapshot.putArray("projectIds");
        projects.forEach(projectIds::add);
        snapshot.set("applications", serializeApplications(applications));
        ArrayNode savedPins = snapshot.putArray("pins");
        pins.forEach(pin -> savedPins.add(store.object().put("studentId", pin.getOwnerId())
                .put(PROJECT_ID_FIELD, store.parse(pin).path(PROJECT_ID_FIELD).asText())
                .put("savedAt", store.parse(pin).path("savedAt").asText())));
        return snapshot;
    }

    private List<StoredRecord> readPins(UUID periodId) {
        return store.repository().findByTypeAndParentId("allocation_pin", periodId.toString()).stream()
                .filter(this::pinTargetIsStudent)
                .sorted(java.util.Comparator.comparing(StoredRecord::getId)).toList();
    }

    private boolean pinTargetIsStudent(StoredRecord pin) {
        try {
            return runtime.isStudent(UUID.fromString(pin.getOwnerId()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private ArrayNode serializeApplications(List<StoredRecord> applications) {
        ArrayNode choices = store.object().arrayNode();
        for (StoredRecord application : applications) {
            ObjectNode choice = choices.addObject();
            choice.put("applicationId", application.getId().toString());
            choice.put(STUDENT_ID_FIELD, application.getOwnerId());
            choice.set("preferences", store.parse(application).path("preferences").deepCopy());
        }
        return choices;
    }

    private boolean isDue(StoredRecord period) {
        return !runtime.now().isBefore(Instant.parse(store.parse(period).path("closesAt").asText()));
    }

    private boolean hasInitialRun(UUID periodId) {
        return store.repository().findByTypeAndParentId(RUN, periodId.toString()).stream()
                .map(store::parse).anyMatch(payload -> payload.path("initial").asBoolean(false));
    }

    private List<String> readIds(JsonNode ids, String field) {
        if (!ids.isArray()) {
            throw new ApiException(HttpStatus.CONFLICT, "The period catalogue is invalid.");
        }
        List<String> result = new ArrayList<>();
        ids.forEach(id -> result.add(id.asText()));
        if (result.size() < AllocationLimits.MIN_GROUP_SIZE) {
            throw new ApiException(HttpStatus.CONFLICT, field + " must include at least three projects.");
        }
        return result;
    }

    private StoredRecord findPeriod(UUID id) {
        return store.repository().findById(id).filter(record -> PERIOD.equals(record.getType()))
                .orElseThrow(() -> new EntityNotFoundException("Application period was not found."));
    }

    private StoredRecord findRun(UUID id) {
        return store.repository().findById(id).filter(record -> RUN.equals(record.getType()))
                .orElseThrow(() -> new EntityNotFoundException("Allocation run was not found."));
    }

    private ObjectNode response(StoredRecord record) {
        ObjectNode response = store.parse(record);
        response.put("id", record.getId().toString());
        response.put("status", record.getStatus());
        response.put("periodId", record.getParentId());
        if (!response.has(EARLY_FIELD)) {
            response.put(EARLY_FIELD, false);
        }
        JsonNode storedResult = response.path(RESULT_FIELD);
        if (storedResult.isObject()) {
            ObjectNode enriched = (ObjectNode) storedResult.deepCopy();
            addDefaultPinnedFlags(enriched.path(ASSIGNMENTS_FIELD));
            addDisplayNames(enriched);
            response.set(RESULT_FIELD, enriched);
        }
        return response;
    }

    private void addDefaultPinnedFlags(JsonNode assignments) {
        if (assignments.isArray()) {
            assignments.forEach(assignment -> {
                if (assignment instanceof ObjectNode object && !object.has("pinned")) {
                    object.put("pinned", false);
                }
            });
        }
    }

    private void addDisplayNames(ObjectNode result) {
        Set<UUID> studentIds = new HashSet<>();
        Set<UUID> projectIds = new HashSet<>();
        collectIds(result.path(ASSIGNMENTS_FIELD), STUDENT_ID_FIELD, studentIds);
        collectIds(result.path(ASSIGNMENTS_FIELD), PROJECT_ID_FIELD, projectIds);
        collectIds(result.path(UNASSIGNED_STUDENTS_FIELD), STUDENT_ID_FIELD, studentIds);
        collectIds(result.path(UNASSIGNED_PROJECTS_FIELD), PROJECT_ID_FIELD, projectIds);

        Map<UUID, String> names = runtime.displayNames(studentIds);
        Map<UUID, String> titles = new HashMap<>();
        store.repository().findAllById(projectIds).stream().filter(project -> "idea".equals(project.getType()))
                .forEach(project -> titles.put(project.getId(), store.parse(project).path("title").asText()));

        addLabels(result.path(ASSIGNMENTS_FIELD), STUDENT_ID_FIELD, "studentDisplayName", names, "Unknown student");
        addLabels(result.path(ASSIGNMENTS_FIELD), PROJECT_ID_FIELD, "projectTitle", titles, "Unknown project");
        addLabels(result.path(UNASSIGNED_STUDENTS_FIELD), STUDENT_ID_FIELD, "studentDisplayName", names, "Unknown student");
        addLabels(result.path(UNASSIGNED_PROJECTS_FIELD), PROJECT_ID_FIELD, "projectTitle", titles, "Unknown project");
        runtime.addStudentDetails(result.path(ASSIGNMENTS_FIELD));
        runtime.addStudentDetails(result.path(UNASSIGNED_STUDENTS_FIELD));
    }

    private void collectIds(JsonNode entries, String field, Set<UUID> ids) {
        if (entries.isArray()) {
            entries.forEach(entry -> {
                UUID id = parseId(entry.path(field).asText());
                if (id != null) {
                    ids.add(id);
                }
            });
        }
    }

    private <T> void addLabels(JsonNode entries, String idField, String labelField, Map<UUID, T> labels,
            String fallback) {
        if (entries.isArray()) {
            entries.forEach(entry -> {
                if (entry instanceof ObjectNode object) {
                    UUID id = parseId(entry.path(idField).asText());
                    String label = id == null ? null : String.valueOf(labels.get(id));
                    object.put(labelField, label == null || "null".equals(label) || label.isBlank() ? fallback : label);
                }
            });
        }
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private PageResponse<ObjectNode> pageResponse(Page<StoredRecord> records) {
        return new PageResponse<>(records.map(this::response).getContent(), records.getNumber(),
                records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
