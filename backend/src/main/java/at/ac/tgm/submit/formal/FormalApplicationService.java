package at.ac.tgm.submit.formal;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import at.ac.tgm.submit.audit.AuditService;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.web.ApiDtos.FormalApplicationResponse;
import at.ac.tgm.submit.web.ApiDtos.FormalReviewResponse;
import at.ac.tgm.submit.web.ApiException;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FormalApplicationService {
    private static final String APPLICATION = "formal_application";
    private static final String REVISION = "formal_application_revision";
    private static final String REVIEW = "formal_application_review";
    private final RecordStore store;
    private final AuditService audit;
    private final FormalApplicationMapper mapper;
    private final FormalApplicationRules rules;

    public FormalApplicationService(RecordStore store, AuditService audit,
            FormalApplicationMapper mapper, FormalApplicationRules rules) {
        this.store = store;
        this.audit = audit;
        this.mapper = mapper;
        this.rules = rules;
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public FormalApplicationResponse create(AuthenticatedUser caller, UUID projectId, JsonNode request) {
        List<String> members = rules.groupMembers(projectId);
        requireMember(caller, members);
        if (store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(APPLICATION,
                projectId.toString()).isPresent()) {
            throw conflict("A formal application already exists for this project.");
        }
        ObjectNode payload = rules.validate(request, members);
        StoredRecord record = store.create(APPLICATION, caller.id().toString(), projectId.toString(),
                "DRAFT", payload);
        audit.record(caller, "FORMAL_APPLICATION_CREATED", APPLICATION, record.getId());
        return mapper.response(record, null);
    }

    @Transactional(readOnly = true)
    public FormalApplicationResponse get(AuthenticatedUser caller, UUID projectId, int page, int size) {
        List<String> members = rules.groupMembers(projectId);
        requireMemberOrTeacher(caller, members);
        StoredRecord application = store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                APPLICATION, projectId.toString())
                .orElseThrow(() -> new EntityNotFoundException("Formal application was not found."));
        Page<StoredRecord> reviews = store.page(REVIEW, null, application.getId().toString(),
                PageRequest.of(page, size));
        return mapper.response(application, mapper.reviewPage(reviews));
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public FormalApplicationResponse update(AuthenticatedUser caller, UUID id, JsonNode request) {
        StoredRecord application = find(id);
        UUID projectId = UUID.fromString(application.getParentId());
        List<String> members = rules.groupMembers(projectId);
        requireMember(caller, members);
        requireEditable(application);
        ObjectNode payload = rules.validate(request, members);
        store.create(REVISION, caller.id().toString(), id.toString(), application.getStatus(),
                store.parse(application));
        store.update(application, "DRAFT", payload);
        audit.record(caller, "FORMAL_APPLICATION_UPDATED", APPLICATION, id);
        return mapper.response(application, null);
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public FormalApplicationResponse submit(AuthenticatedUser caller, UUID id) {
        StoredRecord application = find(id);
        List<String> members = rules.groupMembers(UUID.fromString(application.getParentId()));
        requireMember(caller, members);
        requireEditable(application);
        store.update(application, "SUBMITTED", store.parse(application));
        audit.record(caller, "FORMAL_APPLICATION_SUBMITTED", APPLICATION, id);
        return mapper.response(application, null);
    }

    @Transactional
    @PreAuthorize("hasRole('TEACHER')")
    public FormalReviewResponse review(AuthenticatedUser caller, UUID id, JsonNode request) {
        StoredRecord application = find(id);
        ObjectNode payload = rules.requireObject(request);
        String decision = rules.text(payload, "decision");
        rules.text(payload, "feedback");
        if (!"SUBMITTED".equals(application.getStatus())) {
            throw conflict("Only a submitted formal application can be reviewed.");
        }
        String status = switch (decision) {
            case "APPROVE" -> "APPROVED";
            case "REQUEST_REVISION" -> "NEEDS_REVISION";
            case "DECLINE" -> "DECLINED";
            default -> throw new ApiException(HttpStatus.BAD_REQUEST, "Unsupported formal application decision.");
        };
        StoredRecord review = store.create(REVIEW, caller.id().toString(), id.toString(), decision, payload);
        store.update(application, status, store.parse(application));
        audit.record(caller, "FORMAL_APPLICATION_REVIEWED", REVIEW, review.getId());
        return mapper.reviewResponse(review);
    }

    private void requireMemberOrTeacher(AuthenticatedUser caller, List<String> members) {
        if (caller.hasRole(Role.TEACHER) || caller.hasRole(Role.ADMIN)) {
            return;
        }
        if (!members.contains(caller.id().toString())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only allocated group members can access this application.");
        }
    }

    private void requireMember(AuthenticatedUser caller, List<String> members) {
        if (!members.contains(caller.id().toString())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only allocated group members can author or submit this application.");
        }
    }

    private void requireEditable(StoredRecord application) {
        if (!List.of("DRAFT", "NEEDS_REVISION").contains(application.getStatus())) {
            throw conflict("Submitted formal applications are immutable until returned for revision.");
        }
    }

    private StoredRecord find(UUID id) {
        return store.repository().findById(id).filter(record -> APPLICATION.equals(record.getType()))
                .orElseThrow(() -> new EntityNotFoundException("Formal application was not found."));
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
