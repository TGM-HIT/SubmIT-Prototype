package ac.at.tgm.submit.allocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.audit.AuditService;
import ac.at.tgm.submit.application.ApplyingGroupRecords;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.web.ApiDtos.AllocationPinRequest;
import ac.at.tgm.submit.web.ApiDtos.AllocationPinResponse;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import ac.at.tgm.submit.web.ApiException;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AllocationPinService {
    private static final String PERIOD = "application_period";
    private static final String PIN = "allocation_pin";
    private static final String APPLICATION = "student_application";
    private static final String PROJECT_ID = "projectId";
    private final RecordStore store;
    private final UserAccountRepository users;
    private final AuditService audit;
    private final ApplyingGroupRecords groups;

    public AllocationPinService(RecordStore store, UserAccountRepository users, AuditService audit,
            ApplyingGroupRecords groups) {
        this.store = store;
        this.users = users;
        this.audit = audit;
        this.groups = groups;
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public AllocationPinResponse put(AuthenticatedUser caller, UUID periodId, UUID studentId, AllocationPinRequest request) {
        StoredRecord period = findMutablePeriod(periodId);
        UserAccount student = findStudent(studentId);
        UUID projectId = request.projectId();
        StoredRecord project = findCatalogueProject(period, projectId);
        StoredRecord existing = findExistingPin(periodId, studentId);
        boolean ranked = isRanked(periodId, studentId, projectId);
        requireAcknowledgement(ranked, request);
        removeExisting(existing);
        StoredRecord pin = createPin(periodId, studentId, projectId);
        groups.validatePins(periodId);
        audit.record(caller, existing == null ? "ALLOCATION_PIN_CREATED" : "ALLOCATION_PIN_REPLACED", PIN,
                pin.getId());
        return response(pin, student, project, ranked);
    }

    @Transactional
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public void delete(AuthenticatedUser caller, UUID periodId, UUID studentId) {
        findMutablePeriod(periodId);
        StoredRecord pin = store.repository()
                .findByTypeAndParentIdAndOwnerId(PIN, periodId.toString(), studentId.toString())
                .orElseThrow(() -> new EntityNotFoundException("Allocation pin was not found."));
        UUID pinId = pin.getId();
        audit.record(caller, "ALLOCATION_PIN_REMOVED", PIN, pinId);
        store.delete(pin);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('ROLE_TEACHER','ROLE_ADMIN')")
    public PageResponse<AllocationPinResponse> list(UUID periodId, int page, int size) {
        findPeriod(periodId);
        var pins = store.page(PIN, null, periodId.toString(), PageRequest.of(page, size));
        return new PageResponse<>(pins.getContent().stream().map(this::mapPin).toList(), pins.getNumber(),
                pins.getSize(), pins.getTotalElements(), pins.getTotalPages());
    }

    private AllocationPinResponse mapPin(StoredRecord pin) {
        UUID studentId = UUID.fromString(pin.getOwnerId());
        UserAccount student = users.findById(studentId).orElse(null);
        UUID projectId = UUID.fromString(store.parse(pin).path(PROJECT_ID).asText());
        StoredRecord project = store.repository().findById(projectId).orElse(null);
        boolean ranked = isRanked(UUID.fromString(pin.getParentId()), studentId, projectId);
        return response(pin, student, project, ranked);
    }

    private AllocationPinResponse response(StoredRecord pin, UserAccount student, StoredRecord project,
            boolean ranked) {
        String studentName = student == null ? "Unknown student" : student.getDisplayName();
        String projectTitle = project == null ? "Unknown project" : store.parse(project).path("title").asText();
        return new AllocationPinResponse(UUID.fromString(pin.getOwnerId()), studentName,
                UUID.fromString(store.parse(pin).path(PROJECT_ID).asText()), projectTitle, ranked,
                Instant.parse(store.parse(pin).path("savedAt").asText()));
    }

    private StoredRecord findMutablePeriod(UUID periodId) {
        StoredRecord period = store.lock(PERIOD, periodId);
        if ("FINALIZED".equals(period.getStatus())) {
            throw conflict("A finalized period cannot be changed.");
        }
        return period;
    }

    private UserAccount findStudent(UUID studentId) {
        UserAccount student = users.findById(studentId)
                .orElseThrow(() -> new EntityNotFoundException("Student was not found."));
        if (!student.hasRole(Role.STUDENT)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The target account is not a STUDENT.");
        }
        return student;
    }

    private StoredRecord findCatalogueProject(StoredRecord period, UUID projectId) {
        if (!projectIds(period).contains(projectId.toString())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The project is not in the period catalogue.");
        }
        return store.repository().findById(projectId).filter(record -> "idea".equals(record.getType()))
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST,
                        "The project is not in the period catalogue."));
    }

    private StoredRecord findExistingPin(UUID periodId, UUID studentId) {
        return store.repository().findByTypeAndParentIdAndOwnerId(PIN, periodId.toString(), studentId.toString())
                .orElse(null);
    }

    private boolean isRanked(UUID periodId, UUID studentId, UUID projectId) {
        StoredRecord group = groups.accepted(periodId, studentId.toString());
        if (group != null && "SUBMITTED".equals(group.getStatus())) {
            return groups.ids(store.parse(group).path("projectIds")).contains(projectId.toString());
        }
        return store.repository().findByTypeAndParentIdAndOwnerId(APPLICATION, periodId.toString(),
                        studentId.toString())
                .filter(application -> "SUBMITTED".equals(application.getStatus()))
                .map(application -> hasPreference(application, projectId)).orElse(false);
    }

    private boolean hasPreference(StoredRecord application, UUID projectId) {
        for (JsonNode preference : store.parse(application).path("preferences")) {
            if (projectId.toString().equals(preference.path(PROJECT_ID).asText())) {
                return true;
            }
        }
        return false;
    }

    private void requireAcknowledgement(boolean ranked, AllocationPinRequest request) {
        if (!ranked && !Boolean.TRUE.equals(request.acknowledgeUnranked())) {
            throw conflict("The student did not rank this project; explicit acknowledgement is required.");
        }
    }

    private void removeExisting(StoredRecord existing) {
        if (existing != null) {
            store.delete(existing);
        }
    }

    private StoredRecord createPin(UUID periodId, UUID studentId, UUID projectId) {
        ObjectNode payload = store.object().put(PROJECT_ID, projectId.toString())
                .put("savedAt", Instant.now().toString());
        return store.create(PIN, studentId.toString(), periodId.toString(), "PINNED", payload);
    }

    private java.util.List<String> projectIds(StoredRecord period) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        store.parse(period).path("projectIds").forEach(id -> ids.add(id.asText()));
        return ids;
    }

    private StoredRecord findPeriod(UUID id) {
        return store.repository().findById(id).filter(record -> PERIOD.equals(record.getType()))
                .orElseThrow(() -> new EntityNotFoundException("Application period was not found."));
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
