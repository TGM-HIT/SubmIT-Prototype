package at.ac.tgm.submit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.audit.AuditService;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.web.ApiDtos;
import at.ac.tgm.submit.web.ApiException;
import java.util.Base64;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;

@org.springframework.stereotype.Service
public class ApplicationPeriodService {
    private static final String PERIOD = "application_period";
    private static final String IDEA = "idea";
    private static final String APPLICATION = "student_application";
    private static final String APPLICATION_REVISION = "student_application_revision";
    private static final String PROJECT_IDS = "projectIds";
    private static final String LABEL = "label";
    private static final String SCHOOL_YEAR = "schoolYear";
    private static final String OPENS_AT = "opensAt";
    private static final String CLOSES_AT = "closesAt";
    private static final String STUDENT = "STUDENT";
    private static final String ADMIN = "ADMIN";
    private static final String ELIGIBILITY_MODE = "eligibilityMode";
    private static final String ALL_STUDENTS = "ALL_STUDENTS";
    private static final String ELIGIBLE_STUDENT_IDS = "eligibleStudentIds";
    private static final String ELIGIBLE_CLASS_IDS = "eligibleClassIds";

    private final RecordStore store;
    private final AuditService audit;
    private final ApplicationResponseMapper mapper;
    private final ApplicationPeriodLifecycle lifecycle;

    public ApplicationPeriodService(RecordStore store, AuditService audit, ApplicationResponseMapper mapper,
            ApplicationPeriodLifecycle lifecycle) {
        this.store = store;
        this.audit = audit;
        this.mapper = mapper;
        this.lifecycle = lifecycle;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ApiDtos.PageResponse<String> schoolYears(int page, int size) {
        var currentYear = java.time.Year.now().getValue();
        var years = java.util.List.of(
                currentYear + "/" + (currentYear + 1),
                (currentYear + 1) + "/" + (currentYear + 2),
                (currentYear + 2) + "/" + (currentYear + 3));
        var start = (int) Math.min((long) page * size, years.size());
        var end = Math.min(start + size, years.size());
        var totalPages = (years.size() + size - 1) / size;
        return new ApiDtos.PageResponse<>(years.subList(start, end), page, size, years.size(), totalPages);
    }

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ApiDtos.ApplicationPeriodResponse create(AuthenticatedUser caller, JsonNode request) {
        ObjectNode payload = validatePeriod(request);
        lifecycle.validateEligibility(payload);
        var projectIds = projectIds(payload.path(PROJECT_IDS));
        if (projectIds.size() < 3) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "An application period must include at least three accepted projects.");
        }
        ensureEligibleProjects(projectIds, payload.path(SCHOOL_YEAR).asText());
        var status = periodStatus(payload);
        var period = store.create(PERIOD, caller.id().toString(), null, status, payload);
        audit.record(caller, "APPLICATION_PERIOD_CREATED", PERIOD, period.getId());
        return mapper.period(period, false, null, true);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ApiDtos.PageResponse<ApiDtos.ApplicationPeriodResponse> list(
            AuthenticatedUser caller, int page, int size) {
        return mapper.periods(
                store.page(PERIOD, null, null, org.springframework.data.domain.PageRequest.of(page, size)),
                caller.hasRole(Role.STUDENT) ? caller.id() : null,
                caller.hasRole(Role.ADMIN));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ApiDtos.ApplicationPeriodResponse get(AuthenticatedUser caller, java.util.UUID id) {
        var studentView = caller.hasRole(Role.STUDENT);
        return mapper.period(findPeriod(id), studentView, studentView ? caller.id() : null,
                caller.hasRole(Role.ADMIN));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ApiDtos.CatalogueProjectResponse getCatalogueProject(
            AuthenticatedUser caller, java.util.UUID periodId, java.util.UUID projectId) {
        requireRole(caller, STUDENT);
        var period = findPeriod(periodId);
        requireOpenForStudents(period);
        if (!projectIds(period).contains(projectId.toString())) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "The project is not in this application period.");
        }

        var idea = store.repository().findById(projectId)
                .filter(record -> IDEA.equals(record.getType()))
                .orElseThrow(() -> new ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "The project was not found."));
        var payload = store.parse(idea);
        if (!"ACCEPTED_FOR_APPLICATIONS".equals(idea.getStatus())) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "The project is not available in the current catalogue.");
        }
        var schoolYear = payload.path(SCHOOL_YEAR).asText();
        if (!schoolYear.equals(store.parse(period).path(SCHOOL_YEAR).asText())) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "The project is not in this application period.");
        }
        var specification = store.repository()
                .findFirstByTypeAndParentIdOrderByCreatedAtDesc("specification", idea.getId().toString())
                .orElse(null);
        return mapper.catalogueProject(idea, specification);
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public byte[] cataloguePdf(AuthenticatedUser caller, java.util.UUID periodId, java.util.UUID projectId,
            boolean specification) {
        getCatalogueProject(caller, periodId, projectId);
        StoredRecord document = specification
                ? store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc("specification", projectId.toString())
                        .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                                "PDF document was not found."))
                : store.repository().findById(projectId).orElseThrow(() -> new ApiException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "PDF document was not found."));
        String encoded = store.parse(document).path("privatePdfBase64").asText(null);
        if (encoded == null || encoded.isBlank()) {
            throw new ApiException(org.springframework.http.HttpStatus.NOT_FOUND, "PDF document was not found.");
        }
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Persisted PDF data is invalid.", exception);
        }
    }

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public ApiDtos.ApplicationPeriodResponse update(
            AuthenticatedUser caller, java.util.UUID id, JsonNode request) {
        var period = findPeriodForUpdate(id);
        if ("FINALIZED".equals(period.getStatus())) {
            throw conflict("A finalized application period cannot be changed.");
        }
        var payload = validatePeriod(request);
        lifecycle.validateEligibility(payload);
        var projectIds = projectIds(payload.path(PROJECT_IDS));
        if (projectIds.size() < 3) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "An application period must include at least three accepted projects.");
        }
        ensureEligibleProjects(projectIds, payload.path(SCHOOL_YEAR).asText());
        var proposed = projectIds;
        boolean removesPinnedProject = store.repository().findByTypeAndParentId("allocation_pin", id.toString()).stream()
                .map(pin -> store.parse(pin).path("projectId").asText()).anyMatch(project -> !proposed.contains(project));
        if (removesPinnedProject) {
            throw conflict("A project with allocation pins cannot be removed from the catalogue.");
        }
        lifecycle.ensureInitialRunIfDue(period);
        store.update(period, periodStatus(payload), payload);
        audit.record(caller, "APPLICATION_PERIOD_UPDATED", PERIOD, id);
        return mapper.period(period, false, null, true);
    }

    @org.springframework.transaction.annotation.Transactional
    public ApiDtos.StudentApplicationResponse createApplication(
            AuthenticatedUser caller, java.util.UUID periodId, JsonNode request) {
        requireRole(caller, STUDENT);
        var period = findPeriodForUpdate(periodId);
        requireOpen(period);
        requireStudentEligible(period, caller.id());
        lifecycle.rejectSubmittedSoloWrite(periodId, caller.id().toString());
        if (store.repository().findByTypeAndParentIdAndOwnerId(APPLICATION,
                periodId.toString(), caller.id().toString()).isPresent()) {
            throw conflict("A student may submit only one application per period.");
        }
        var payload = validateApplication(request, period);
        var application = store.create(APPLICATION, caller.id().toString(),
                periodId.toString(), "SUBMITTED", payload);
        audit.record(caller, "APPLICATION_SUBMITTED", APPLICATION, application.getId());
        return mapper.studentApplication(application, mapper.studentProjectTitles(period));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ApiDtos.StudentApplicationResponse myApplication(
            AuthenticatedUser caller, java.util.UUID periodId) {
        requireRole(caller, STUDENT);
        var shared = lifecycle.acceptedGroup(periodId, caller.id().toString());
        if (shared != null && "SUBMITTED".equals(shared.getStatus())) {
            return mapper.sharedApplication(shared, caller.id());
        }
        var application = store.repository().findByTypeAndParentIdAndOwnerId(APPLICATION,
                periodId.toString(), caller.id().toString())
                .orElseThrow(() -> new EntityNotFoundException("Application was not found."));
        var period = findPeriod(periodId);
        return mapper.studentApplication(application, mapper.studentProjectTitles(period));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public ApiDtos.PageResponse<ApiDtos.StudentApplicationResponse> applications(
            AuthenticatedUser caller, java.util.UUID periodId, int page, int size) {
        if (caller.hasRole(Role.STUDENT)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only teachers and administrators can list applications.");
        }
        findPeriod(periodId);
        return mapper.applications(store.page(APPLICATION, null, periodId.toString(),
                org.springframework.data.domain.PageRequest.of(page, size)));
    }

    @org.springframework.transaction.annotation.Transactional
    public ApiDtos.StudentApplicationResponse updateApplication(
            AuthenticatedUser caller, java.util.UUID id, JsonNode request) {
        var application = findApplication(id);
        requireOwner(caller, application);
        var period = findPeriodForUpdate(java.util.UUID.fromString(application.getParentId()));
        store.refresh(application);
        requireOpen(period);
        requireStudentEligible(period, caller.id());
        lifecycle.rejectSubmittedSoloWrite(period.getId(), caller.id().toString());
        var payload = validateApplication(request, period);
        store.create(APPLICATION_REVISION, caller.id().toString(), id.toString(),
                application.getStatus(), store.parse(application));
        store.update(application, "SUBMITTED", payload);
        audit.record(caller, "APPLICATION_UPDATED", APPLICATION, id);
        return mapper.studentApplication(application, mapper.studentProjectTitles(period));
    }

    @org.springframework.transaction.annotation.Transactional
    public void withdrawApplication(AuthenticatedUser caller, java.util.UUID id) {
        var application = findApplication(id);
        requireOwner(caller, application);
        var period = findPeriodForUpdate(java.util.UUID.fromString(application.getParentId()));
        store.refresh(application);
        requireOpen(period);
        lifecycle.rejectSubmittedSoloWrite(period.getId(), caller.id().toString());
        store.create(APPLICATION_REVISION, caller.id().toString(), id.toString(),
                application.getStatus(), store.parse(application));
        store.update(application, "WITHDRAWN", store.parse(application));
        audit.record(caller, "APPLICATION_WITHDRAWN", APPLICATION, id);
    }

    private ObjectNode validatePeriod(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Request body must be a JSON object.");
        }
        var payload = (ObjectNode) request.deepCopy();
        normalizePeriodFields(payload);
        projectIds(payload.path(PROJECT_IDS));
        defaultEligibility(payload);
        return payload;
    }

    private void normalizePeriodFields(ObjectNode payload) {
        var label = text(payload, LABEL).trim();
        if (label.isBlank() || label.length() > 100) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "label must contain 1 to 100 non-blank characters.");
        }
        var schoolYear = text(payload, SCHOOL_YEAR);
        var openingAt = instant(payload, OPENS_AT);
        var closingAt = instant(payload, CLOSES_AT);
        if (!closingAt.isAfter(openingAt)) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    CLOSES_AT + " must be later than " + OPENS_AT + ".");
        }
        payload.put(LABEL, label);
        payload.put(SCHOOL_YEAR, schoolYear);
        payload.put(OPENS_AT, openingAt.toString());
        payload.put(CLOSES_AT, closingAt.toString());
    }

    private void defaultEligibility(ObjectNode payload) {
        if (!payload.has(ELIGIBILITY_MODE) || payload.path(ELIGIBILITY_MODE).isNull()) {
            payload.put(ELIGIBILITY_MODE, ALL_STUDENTS);
        }
        if (!payload.has(ELIGIBLE_STUDENT_IDS) || payload.path(ELIGIBLE_STUDENT_IDS).isNull()) {
            payload.putArray(ELIGIBLE_STUDENT_IDS);
        }
        if (!payload.has(ELIGIBLE_CLASS_IDS) || payload.path(ELIGIBLE_CLASS_IDS).isNull()) {
            payload.putArray(ELIGIBLE_CLASS_IDS);
        }
    }

    private void requireStudentEligible(StoredRecord period, java.util.UUID studentId) {
        if (!lifecycle.isEligible(period, studentId)) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "You are not eligible to apply in this application period.");
        }
    }

    private ApiException badRequest(String message) {
        return new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, message);
    }

    private ObjectNode validateApplication(JsonNode request, StoredRecord period) {
        if (request == null || !request.isObject()) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Request body must be a JSON object.");
        }
        var payload = (ObjectNode) request.deepCopy();
        var projectIds = projectIds(payload.path(PROJECT_IDS));
        if (projectIds.size() < 3) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "At least three distinct project preferences are required.");
        }
        var catalogue = projectIds(periodPayload(period).path(PROJECT_IDS));
        if (!catalogue.containsAll(projectIds)) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Every preference must be an accepted project in the period catalogue.");
        }
        var ranked = store.object().arrayNode();
        for (int index = 0; index < projectIds.size(); index++) {
            ranked.add(store.object().put("projectId", projectIds.get(index)).put("rank", index + 1));
        }
        payload.set("preferences", ranked);
        return payload;
    }

    private void ensureEligibleProjects(java.util.List<String> ids, String schoolYear) {
        for (String id : ids) {
            StoredRecord idea = store.repository().findById(java.util.UUID.fromString(id))
                    .filter(record -> IDEA.equals(record.getType()))
                    .orElseThrow(() -> new ApiException(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "A catalog project does not exist."));
            if (!"ACCEPTED_FOR_APPLICATIONS".equals(idea.getStatus())) {
                throw new ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Only accepted projects can be catalogued.");
            }
            if (!schoolYear.equals(store.parse(idea).path(SCHOOL_YEAR).asText())) {
                throw new ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Every catalog project must match the application period school year.");
            }
        }
    }

    private String periodStatus(ObjectNode payload) {
        var now = lifecycle.now();
        var opening = Instant.parse(payload.path(OPENS_AT).asText());
        var closing = Instant.parse(payload.path(CLOSES_AT).asText());
        if (now.isBefore(opening)) {
            return "DRAFT";
        }
        if (now.isBefore(closing)) {
            return "OPEN";
        }
        return "CLOSED";
    }

    private void requireOpen(StoredRecord period) {
        var payload = periodPayload(period);
        var now = lifecycle.now();
        var closing = java.time.Instant.parse(payload.path(CLOSES_AT).asText());
        var opening = java.time.Instant.parse(payload.path(OPENS_AT).asText());
        if ("FINALIZED".equals(period.getStatus()) || now.isBefore(opening) || !now.isBefore(closing)) {
            throw conflict("Applications are not open for this period.");
        }
    }

    private java.util.List<String> projectIds(JsonNode node) {
        if (node == null || !node.isArray()) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    PROJECT_IDS + " must be an array.");
        }
        var ids = new java.util.ArrayList<String>();
        for (JsonNode value : node) {
            try {
                var id = java.util.UUID.fromString(value.asText()).toString();
                if (ids.contains(id)) {
                    throw new ApiException(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "Project IDs must be distinct.");
                }
                ids.add(id);
            } catch (IllegalArgumentException exception) {
                throw new ApiException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Each project ID must be a UUID.");
            }
        }
        return ids;
    }

    private java.util.List<String> projectIds(StoredRecord period) {
        var ids = new java.util.ArrayList<String>();
        periodPayload(period).path(PROJECT_IDS).forEach(project -> ids.add(project.asText()));
        return ids;
    }

    private java.time.Instant instant(ObjectNode payload, String field) {
        try {
            return java.time.Instant.parse(text(payload, field));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    field + " must be an ISO-8601 timestamp.");
        }
    }

    private String text(ObjectNode payload, String field) {
        var value = payload.path(field).asText("").trim();
        if (value.isEmpty()) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    field + " is required.");
        }
        return value;
    }

    private ObjectNode periodPayload(StoredRecord period) {
        return store.parse(period);
    }

    private void requireOpenForStudents(StoredRecord period) {
        var payload = periodPayload(period);
        var now = lifecycle.now();
        var closing = java.time.Instant.parse(payload.path(CLOSES_AT).asText());
        var opening = java.time.Instant.parse(payload.path(OPENS_AT).asText());
        if (now.isBefore(opening) || now.isAfter(closing)) {
            throw new ApiException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "This application period is not currently open.");
        }
    }

    private StoredRecord findPeriod(java.util.UUID id) {
        return store.repository().findById(id).filter(record -> PERIOD.equals(record.getType()))
                .orElseThrow(() -> new EntityNotFoundException("Application period was not found."));
    }

    private StoredRecord findPeriodForUpdate(java.util.UUID id) {
        return store.lock(PERIOD, id);
    }

    private StoredRecord findApplication(java.util.UUID id) {
        return store.repository().findById(id).filter(record -> APPLICATION.equals(record.getType()))
                .orElseThrow(() -> new EntityNotFoundException("Application was not found."));
    }

    private void requireOwner(AuthenticatedUser caller, StoredRecord record) {
        requireRole(caller, STUDENT);
        if (!caller.id().toString().equals(record.getOwnerId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This application belongs to another user.");
        }
    }

    private void requireRole(AuthenticatedUser caller, String roleName) {
        if (!caller.hasRole(Role.valueOf(roleName))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "This operation requires the " + roleName + " role.");
        }
    }

    private ApiException conflict(String message) {
        return new ApiException(org.springframework.http.HttpStatus.CONFLICT, message);
    }
}
