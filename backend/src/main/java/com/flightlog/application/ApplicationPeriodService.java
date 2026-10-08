package com.flightlog.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.audit.AuditService;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.security.Role;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiException;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.Year;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationPeriodService {
    private static final String PERIOD = "application_period";
    private static final String IDEA = "idea";
    private static final String APPLICATION = "student_application";
    private static final String APPLICATION_REVISION = "student_application_revision";
    private static final String PROJECT_IDS = "projectIds";
    private static final String SCHOOL_YEAR = "schoolYear";
    private static final String OPENS_AT = "opensAt";
    private static final String CLOSES_AT = "closesAt";

    private final RecordStore store;
    private final AuditService audit;
    private final ApplicationResponseMapper mapper;

    public ApplicationPeriodService(RecordStore store, AuditService audit, ApplicationResponseMapper mapper) {
        this.store = store;
        this.audit = audit;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<String> schoolYears(int page, int size) {
        int currentYear = Year.now().getValue();
        List<String> years = List.of(currentYear + "/" + (currentYear + 1),
                (currentYear + 1) + "/" + (currentYear + 2), (currentYear + 2) + "/" + (currentYear + 3));
        int start = (int) Math.min((long) page * size, years.size());
        int end = Math.min(start + size, years.size());
        int totalPages = (years.size() + size - 1) / size;
        return new PageResponse<>(years.subList(start, end), page, size, years.size(), totalPages);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public com.flightlog.web.ApiDtos.ApplicationPeriodResponse create(AuthenticatedUser caller, JsonNode request) {
        ObjectNode payload = validatePeriod(request);
        List<String> projectIds = projectIds(payload.path(PROJECT_IDS));
        if (projectIds.size() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "An application period must include at least three accepted projects.");
        }
        ensureEligibleProjects(projectIds, payload.path(SCHOOL_YEAR).asText());
        String status = periodStatus(payload);
        StoredRecord period = store.create(PERIOD, caller.id().toString(), null, status, payload);
        audit.record(caller, "APPLICATION_PERIOD_CREATED", PERIOD, period.getId());
        return mapper.period(period);
    }

    @Transactional(readOnly = true)
    public PageResponse<com.flightlog.web.ApiDtos.ApplicationPeriodResponse> list(int page, int size) {
        return mapper.periods(store.page(PERIOD, null, null, PageRequest.of(page, size)));
    }

    @Transactional(readOnly = true)
    public com.flightlog.web.ApiDtos.ApplicationPeriodResponse get(UUID id) {
        return mapper.period(findPeriod(id));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public com.flightlog.web.ApiDtos.ApplicationPeriodResponse update(
            AuthenticatedUser caller, UUID id, JsonNode request) {
        StoredRecord period = findPeriod(id);
        if ("FINALIZED".equals(period.getStatus())) {
            throw conflict("A finalized application period cannot be changed.");
        }
        ObjectNode payload = validatePeriod(request);
        List<String> projectIds = projectIds(payload.path(PROJECT_IDS));
        if (projectIds.size() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "An application period must include at least three accepted projects.");
        }
        ensureEligibleProjects(projectIds, payload.path(SCHOOL_YEAR).asText());
        store.update(period, periodStatus(payload), payload);
        audit.record(caller, "APPLICATION_PERIOD_UPDATED", PERIOD, id);
        return mapper.period(period);
    }

    @Transactional
    public com.flightlog.web.ApiDtos.StudentApplicationResponse createApplication(
            AuthenticatedUser caller, UUID periodId, JsonNode request) {
        requireRole(caller, Role.STUDENT);
        StoredRecord period = findPeriod(periodId);
        requireOpen(period);
        if (store.repository().findByTypeAndParentIdAndOwnerId(APPLICATION,
                periodId.toString(), caller.id().toString()).isPresent()) {
            throw conflict("A student may submit only one application per period.");
        }
        ObjectNode payload = validateApplication(request, period);
        StoredRecord application = store.create(APPLICATION, caller.id().toString(),
                periodId.toString(), "SUBMITTED", payload);
        audit.record(caller, "APPLICATION_SUBMITTED", APPLICATION, application.getId());
        return mapper.application(application);
    }

    @Transactional(readOnly = true)
    public com.flightlog.web.ApiDtos.StudentApplicationResponse myApplication(
            AuthenticatedUser caller, UUID periodId) {
        requireRole(caller, Role.STUDENT);
        StoredRecord application = store.repository().findByTypeAndParentIdAndOwnerId(APPLICATION,
                periodId.toString(), caller.id().toString())
                .orElseThrow(() -> new EntityNotFoundException("Application was not found."));
        return mapper.application(application);
    }

    @Transactional(readOnly = true)
    public PageResponse<com.flightlog.web.ApiDtos.StudentApplicationResponse> applications(
            AuthenticatedUser caller, UUID periodId, int page, int size) {
        if (caller.role() == Role.STUDENT) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only teachers and administrators can list applications.");
        }
        findPeriod(periodId);
        return mapper.applications(store.page(APPLICATION, null, periodId.toString(),
                PageRequest.of(page, size)));
    }

    @Transactional
    public com.flightlog.web.ApiDtos.StudentApplicationResponse updateApplication(
            AuthenticatedUser caller, UUID id, JsonNode request) {
        StoredRecord application = findApplication(id);
        requireOwner(caller, application);
        StoredRecord period = findPeriod(UUID.fromString(application.getParentId()));
        requireOpen(period);
        ObjectNode payload = validateApplication(request, period);
        store.create(APPLICATION_REVISION, caller.id().toString(), id.toString(),
                application.getStatus(), store.parse(application));
        store.update(application, "SUBMITTED", payload);
        audit.record(caller, "APPLICATION_UPDATED", APPLICATION, id);
        return mapper.application(application);
    }

    @Transactional
    public void withdrawApplication(AuthenticatedUser caller, UUID id) {
        StoredRecord application = findApplication(id);
        requireOwner(caller, application);
        StoredRecord period = findPeriod(UUID.fromString(application.getParentId()));
        requireOpen(period);
        store.create(APPLICATION_REVISION, caller.id().toString(), id.toString(),
                application.getStatus(), store.parse(application));
        store.update(application, "WITHDRAWN", store.parse(application));
        audit.record(caller, "APPLICATION_WITHDRAWN", APPLICATION, id);
    }

    private ObjectNode validatePeriod(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body must be a JSON object.");
        }
        ObjectNode payload = (ObjectNode) request.deepCopy();
        String schoolYear = text(payload, SCHOOL_YEAR);
        Instant openingAt = instant(payload, OPENS_AT);
        Instant closingAt = instant(payload, CLOSES_AT);
        if (!closingAt.isAfter(openingAt)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, CLOSES_AT + " must be later than " + OPENS_AT + ".");
        }
        payload.put(SCHOOL_YEAR, schoolYear);
        payload.put(OPENS_AT, openingAt.toString());
        payload.put(CLOSES_AT, closingAt.toString());
        projectIds(payload.path(PROJECT_IDS));
        return payload;
    }

    private ObjectNode validateApplication(JsonNode request, StoredRecord period) {
        if (request == null || !request.isObject()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Request body must be a JSON object.");
        }
        ObjectNode payload = (ObjectNode) request.deepCopy();
        List<String> projectIds = projectIds(payload.path(PROJECT_IDS));
        if (projectIds.size() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "At least three distinct project preferences are required.");
        }
        Set<String> catalogue = new HashSet<>();
        periodPayload(period).path(PROJECT_IDS).forEach(project -> catalogue.add(project.asText()));
        if (!catalogue.containsAll(projectIds)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Every preference must be an accepted project in the period catalogue.");
        }
        ArrayNode ranked = store.object().arrayNode();
        for (int index = 0; index < projectIds.size(); index++) {
            ranked.add(store.object().put("projectId", projectIds.get(index)).put("rank", index + 1));
        }
        payload.set("preferences", ranked);
        payload.remove(PROJECT_IDS);
        return payload;
    }

    private void ensureEligibleProjects(List<String> ids, String schoolYear) {
        for (String id : ids) {
            StoredRecord idea = store.repository().findById(UUID.fromString(id))
                    .filter(record -> IDEA.equals(record.getType()))
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "A catalog project does not exist."));
            if (!"ACCEPTED_FOR_APPLICATIONS".equals(idea.getStatus())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Only accepted projects can be catalogued.");
            }
            if (!schoolYear.equals(store.parse(idea).path(SCHOOL_YEAR).asText())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Every catalog project must match the application period school year.");
            }
        }
    }

    private String periodStatus(ObjectNode payload) {
        Instant now = Instant.now();
        Instant opening = Instant.parse(payload.path(OPENS_AT).asText());
        Instant closing = Instant.parse(payload.path(CLOSES_AT).asText());
        if (now.isBefore(opening)) {
            return "DRAFT";
        }
        if (!now.isAfter(closing)) {
            return "OPEN";
        }
        return "CLOSED";
    }

    private void requireOpen(StoredRecord period) {
        ObjectNode payload = periodPayload(period);
        Instant now = Instant.now();
        Instant closing = Instant.parse(payload.path(CLOSES_AT).asText());
        Instant opening = Instant.parse(payload.path(OPENS_AT).asText());
        if (!"OPEN".equals(period.getStatus()) || now.isBefore(opening) || now.isAfter(closing)) {
            throw conflict("Applications are not open for this period.");
        }
    }

    private List<String> projectIds(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, PROJECT_IDS + " must be an array.");
        }
        List<String> ids = new ArrayList<>();
        for (JsonNode value : node) {
            try {
                String id = UUID.fromString(value.asText()).toString();
                if (ids.contains(id)) {
                    throw new ApiException(HttpStatus.BAD_REQUEST, "Project IDs must be distinct.");
                }
                ids.add(id);
            } catch (IllegalArgumentException exception) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Each project ID must be a UUID.");
            }
        }
        return ids;
    }

    private Instant instant(ObjectNode payload, String field) {
        try {
            return Instant.parse(text(payload, field));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " must be an ISO-8601 timestamp.");
        }
    }

    private String text(ObjectNode payload, String field) {
        String value = payload.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, field + " is required.");
        }
        return value;
    }

    private ObjectNode periodPayload(StoredRecord period) {
        return store.parse(period);
    }

    private StoredRecord findPeriod(UUID id) {
        return store.repository().findById(id).filter(record -> PERIOD.equals(record.getType()))
                .orElseThrow(() -> new EntityNotFoundException("Application period was not found."));
    }

    private StoredRecord findApplication(UUID id) {
        return store.repository().findById(id).filter(record -> APPLICATION.equals(record.getType()))
                .orElseThrow(() -> new EntityNotFoundException("Application was not found."));
    }

    private void requireOwner(AuthenticatedUser caller, StoredRecord record) {
        requireRole(caller, Role.STUDENT);
        if (!caller.id().toString().equals(record.getOwnerId())) {
            throw new org.springframework.security.access.AccessDeniedException("This application belongs to another user.");
        }
    }

    private void requireRole(AuthenticatedUser caller, Role role) {
        if (caller.role() != role) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This operation requires the " + role + " role.");
        }
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

}
