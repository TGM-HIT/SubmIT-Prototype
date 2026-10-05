package com.flightlog.allocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.audit.AuditService;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiException;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private static final int MAX_APPLICATIONS = AllocationLimits.MAX_STUDENTS;

    private final RecordStore store;
    private final AuditService audit;
    private final AllocationEngine engine;

    public AllocationService(RecordStore store, AuditService audit, AllocationEngine engine) {
        this.store = store;
        this.audit = audit;
        this.engine = engine;
    }

    @Transactional
    public int startDuePeriods() {
        Page<StoredRecord> periods = store.page(PERIOD, null, null, PageRequest.of(0, 100));
        int started = 0;
        for (StoredRecord period : periods.getContent()) {
            if (isDue(period) && !hasInitialRun(period.getId())) {
                createRun(null, period, true);
                started++;
            }
        }
        return started;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ObjectNode startReplacement(AuthenticatedUser caller, UUID periodId) {
        StoredRecord period = findPeriod(periodId);
        if (FINALIZED.equals(period.getStatus())) {
            throw conflict("A finalized allocation cannot be replaced.");
        }
        if (Instant.now().isBefore(Instant.parse(store.parse(period).path("closesAt").asText()))) {
            throw conflict("An allocation run can start only after the application deadline.");
        }
        store.update(period, "CLOSED", store.parse(period));
        StoredRecord run = createRun(caller, period, false);
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
        if (!"COMPLETED".equals(run.getStatus())) {
            throw conflict("Only a completed allocation can be finalized.");
        }
        StoredRecord period = findPeriod(UUID.fromString(run.getParentId()));
        if (FINALIZED.equals(period.getStatus())) {
            throw conflict("This application period is already finalized.");
        }
        ObjectNode payload = store.parse(run);
        JsonNode assignments = payload.path("result").path("assignments");
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (JsonNode assignment : assignments) {
            groups.computeIfAbsent(assignment.path("projectId").asText(), ignored -> new ArrayList<>())
                    .add(assignment.path("studentId").asText());
        }
        for (Map.Entry<String, List<String>> group : groups.entrySet()) {
            ObjectNode groupPayload = store.object().put("projectId", group.getKey())
                    .put("periodId", period.getId().toString()).put("allocationRunId", id.toString());
            ArrayNode students = groupPayload.putArray("studentIds");
            group.getValue().forEach(students::add);
            store.create(PROJECT_GROUP, caller.id().toString(), group.getKey(), "ALLOCATED", groupPayload);
        }
        payload.put("finalizedAt", Instant.now().toString());
        store.update(run, FINALIZED, payload);
        store.update(period, FINALIZED, store.parse(period));
        audit.record(caller, "ALLOCATION_FINALIZED", RUN, id);
        return response(run);
    }

    private StoredRecord createRun(AuthenticatedUser caller, StoredRecord period, boolean initial) {
        ObjectNode periodData = store.parse(period);
        List<String> projectIds = readIds(periodData.path("projectIds"), "projectIds");
        ObjectNode snapshot = snapshot(projectIds, List.of());
        snapshot.put("initial", initial);
        snapshot.put("createdAt", Instant.now().toString());
        StoredRecord run = store.create(RUN, caller == null ? null : caller.id().toString(),
                period.getId().toString(), "RUNNING", snapshot);
        try {
            List<StoredRecord> applications = readApplications(period.getId());
            snapshot.set("applications", serializeApplications(applications));
            Object result = engine.optimize(projectIds, applications);
            snapshot.set("result", store.value(result));
            store.update(run, "COMPLETED", snapshot);
            store.update(period, "ALLOCATED", periodData);
        } catch (AllocationLimitExceededException | IllegalArgumentException exception) {
            snapshot.put("error", exception.getMessage());
            snapshot.put("inputSnapshotComplete", false);
            store.update(run, "FAILED", snapshot);
            store.update(period, "CLOSED", periodData);
        }
        if (caller != null) {
            audit.record(caller, "ALLOCATION_RUN_STARTED", RUN, run.getId());
        } else {
            audit.recordSystem("ALLOCATION_RUN_STARTED", RUN, run.getId());
        }
        return run;
    }

    private List<StoredRecord> readApplications(UUID periodId) {
        Page<StoredRecord> page = store.page(APPLICATION, null, periodId.toString(),
                PageRequest.of(0, MAX_APPLICATIONS + 1));
        if (page.getTotalElements() > MAX_APPLICATIONS) {
            throw new AllocationLimitExceededException("The exact allocation supports at most "
                    + MAX_APPLICATIONS + " student applications.");
        }
        return page.getContent().stream().filter(application -> "SUBMITTED".equals(application.getStatus())).toList();
    }

    private ObjectNode snapshot(List<String> projects, List<StoredRecord> applications) {
        ObjectNode snapshot = store.object();
        ArrayNode projectIds = snapshot.putArray("projectIds");
        projects.forEach(projectIds::add);
        snapshot.set("applications", serializeApplications(applications));
        return snapshot;
    }

    private ArrayNode serializeApplications(List<StoredRecord> applications) {
        ArrayNode choices = store.object().arrayNode();
        for (StoredRecord application : applications) {
            ObjectNode choice = choices.addObject();
            choice.put("applicationId", application.getId().toString());
            choice.put("studentId", application.getOwnerId());
            choice.set("preferences", store.parse(application).path("preferences").deepCopy());
        }
        return choices;
    }

    private boolean isDue(StoredRecord period) {
        if (!"OPEN".equals(period.getStatus())) {
            return false;
        }
        return !Instant.now().isBefore(Instant.parse(store.parse(period).path("closesAt").asText()));
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
        ObjectNode result = store.parse(record);
        result.put("id", record.getId().toString());
        result.put("status", record.getStatus());
        result.put("periodId", record.getParentId());
        return result;
    }

    private PageResponse<ObjectNode> pageResponse(Page<StoredRecord> records) {
        return new PageResponse<>(records.map(this::response).getContent(), records.getNumber(),
                records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
