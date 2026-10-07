package com.flightlog.idea;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.audit.AuditService;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.security.Role;
import com.flightlog.web.ApiDtos;
import com.flightlog.web.ApiException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdeaService {
    private static final String IDEA = "idea";
    private static final String SPECIFICATION = "specification";
    private static final String REVIEW = "review";
    private static final String IDEA_DRAFT = "IDEA_DRAFT";
    private static final String IDEA_SUBMITTED = "IDEA_SUBMITTED";
    private static final String IDEA_NEEDS_REVISION = "IDEA_NEEDS_REVISION";
    private static final String IDEA_APPROVED = "IDEA_APPROVED_FOR_SPECIFICATION";
    private static final String SPEC_SUBMITTED = "SPECIFICATION_SUBMITTED";
    private static final String SPEC_NEEDS_REVISION = "SPECIFICATION_NEEDS_REVISION";
    private static final String SPEC_DRAFT = "SPECIFICATION_DRAFT";
    private static final String LEARNING_TOPICS = "learningTopics";
    private final RecordStore store;
    private final AuditService audit;
    private final IdeaResponseMapper mapper;
    private final IdeaRules rules;

    public IdeaService(RecordStore store, AuditService audit, IdeaResponseMapper mapper, IdeaRules rules) {
        this.store = store;
        this.audit = audit;
        this.mapper = mapper;
        this.rules = rules;
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public ApiDtos.IdeaResponse create(AuthenticatedUser caller, JsonNode request) {
        ObjectNode payload = rules.object(request);
        validateIdeaPayload(payload);
        StoredRecord record = store.create(IDEA, caller.id().toString(), null, IDEA_DRAFT, payload);
        audit.record(caller, "IDEA_CREATED", IDEA, record.getId());
        return mapper.response(record, false);
    }

    @Transactional(readOnly = true)
    public ApiDtos.PageResponse<ApiDtos.IdeaResponse> list(
            AuthenticatedUser caller, String status, int page, int size) {
        Page<StoredRecord> records = findIdeas(caller, status, PageRequest.of(page, size));
        Page<ApiDtos.IdeaResponse> results = records.map(record -> mapper.response(record, false));
        return pageResponse(results);
    }

    @Transactional(readOnly = true)
    public ApiDtos.IdeaResponse get(AuthenticatedUser caller, UUID id) {
        StoredRecord idea = findIdea(id);
        requireVisible(caller, idea);
        return mapper.response(idea, true);
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public ApiDtos.IdeaResponse update(AuthenticatedUser caller, UUID id, JsonNode request) {
        StoredRecord idea = findIdea(id);
        requireOwner(caller, idea);
        rules.status(idea, IDEA_DRAFT, IDEA_NEEDS_REVISION);
        ObjectNode payload = rules.object(request);
        validateIdeaPayload(payload);
        store.update(idea, idea.getStatus(), payload);
        audit.record(caller, "IDEA_UPDATED", IDEA, id);
        return mapper.response(idea, false);
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public ApiDtos.IdeaResponse submit(AuthenticatedUser caller, UUID id) {
        StoredRecord idea = findIdea(id);
        requireOwner(caller, idea);
        rules.status(idea, IDEA_DRAFT, IDEA_NEEDS_REVISION);
        store.update(idea, IDEA_SUBMITTED, store.parse(idea));
        audit.record(caller, "IDEA_SUBMITTED", IDEA, id);
        return mapper.response(idea, false);
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public ApiDtos.SpecificationResponse saveSpecification(AuthenticatedUser caller, UUID id, JsonNode request) {
        StoredRecord idea = findIdea(id);
        requireOwner(caller, idea);
        rules.status(idea, IDEA_APPROVED, SPEC_NEEDS_REVISION);
        ObjectNode payload = rules.object(request);
        rules.nonEmptyArray(payload, "objectives");
        rules.nonEmptyArray(payload, "intendedUsers");
        rules.nonEmptyArray(payload, "requirements");
        rules.nonEmptyArray(payload, "deliverables");
        rules.text(payload, "scope");
        rules.nonEmptyArray(payload, "dependencies");
        StoredRecord current = store.repository()
                .findFirstByTypeAndParentIdOrderByCreatedAtDesc(SPECIFICATION, id.toString()).orElse(null);
        if (current != null && !SPEC_DRAFT.equals(current.getStatus())
                && !SPEC_NEEDS_REVISION.equals(current.getStatus())) {
            throw rules.conflict("A submitted specification cannot be edited until returned for revision.");
        }
        StoredRecord revision = store.create("specification_revision", caller.id().toString(), id.toString(),
                "SAVED", payload.deepCopy());
        payload.put("revisionId", revision.getId().toString());
        if (current == null) {
            current = store.create(SPECIFICATION, caller.id().toString(), id.toString(),
                    "SPECIFICATION_DRAFT", payload);
        } else {
            store.update(current, SPEC_NEEDS_REVISION, payload);
        }
        audit.record(caller, "SPECIFICATION_UPDATED", SPECIFICATION, current.getId());
        return mapper.specificationResponse(current);
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public ApiDtos.SpecificationResponse submitSpecification(AuthenticatedUser caller, UUID id) {
        StoredRecord idea = findIdea(id);
        requireOwner(caller, idea);
        rules.status(idea, IDEA_APPROVED, SPEC_NEEDS_REVISION);
        StoredRecord specification = store.repository()
                .findFirstByTypeAndParentIdOrderByCreatedAtDesc(SPECIFICATION, id.toString())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "A full specification is required."));
        rules.status(specification, SPEC_DRAFT, SPEC_NEEDS_REVISION);
        store.update(specification, SPEC_SUBMITTED, store.parse(specification));
        store.update(idea, SPEC_SUBMITTED, store.parse(idea));
        audit.record(caller, "SPECIFICATION_SUBMITTED", SPECIFICATION, specification.getId());
        return mapper.specificationResponse(specification);
    }

    @Transactional
    @PreAuthorize("hasRole('TEACHER')")
    public ApiDtos.ReviewResponse review(
            AuthenticatedUser caller, UUID id, boolean specificationReview, JsonNode request) {
        StoredRecord idea = findIdea(id);
        ObjectNode payload = rules.object(request);
        String decision = rules.text(payload, "decision");
        rules.text(payload, "feedback");
        rules.ratings(payload);
        payload.put("reviewType", specificationReview ? "SPECIFICATION" : "IDEA");
        String expectedStatus = specificationReview ? SPEC_SUBMITTED : IDEA_SUBMITTED;
        if (!expectedStatus.equals(idea.getStatus())) {
            throw rules.conflict("The requested decision is not valid at this lifecycle stage.");
        }
        String nextStatus = rules.nextReviewStatus(decision, specificationReview);
        StoredRecord review = store.create(REVIEW, caller.id().toString(), id.toString(), decision, payload);
        store.update(idea, nextStatus, store.parse(idea));
        if (specificationReview) {
            StoredRecord specification = store.repository()
                    .findFirstByTypeAndParentIdOrderByCreatedAtDesc(SPECIFICATION, id.toString())
                    .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "A full specification is required."));
            store.update(specification, nextStatus, store.parse(specification));
        }
        audit.record(caller, "IDEA_REVIEW_RECORDED", IDEA, review.getId());
        return mapper.reviewResponse(review);
    }

    @Transactional
    @PreAuthorize("hasRole('STUDENT')")
    public ApiDtos.IdeaResponse withdraw(AuthenticatedUser caller, UUID id) {
        StoredRecord idea = findIdea(id);
        requireOwner(caller, idea);
        rules.status(idea, IDEA_DRAFT, IDEA_SUBMITTED, IDEA_NEEDS_REVISION, IDEA_APPROVED,
                SPEC_SUBMITTED, SPEC_NEEDS_REVISION);
        store.update(idea, "WITHDRAWN", store.parse(idea));
        audit.record(caller, "IDEA_WITHDRAWN", IDEA, id);
        return mapper.response(idea, false);
    }

    private void validateIdeaPayload(ObjectNode payload) {
        rules.text(payload, "title");
        rules.text(payload, "description");
        rules.text(payload, "intendedOutcome");
        rules.nonEmptyArray(payload, "scopePoints");
        rules.optionalArray(payload, LEARNING_TOPICS);
        rules.text(payload, "schoolYear");
    }

    private StoredRecord findIdea(UUID id) {
        return store.repository().findById(id).filter(record -> IDEA.equals(record.getType()))
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Idea was not found."));
    }

    private Page<StoredRecord> findIdeas(AuthenticatedUser caller, String status, PageRequest page) {
        boolean hasStatus = status != null && !status.isBlank();
        if (caller.role() == Role.STUDENT) {
            if (hasStatus) {
                return store.repository().findByTypeAndOwnerIdAndStatus(IDEA,
                        caller.id().toString(), status, page);
            }
            return store.page(IDEA, caller.id().toString(), null, page);
        }
        if (hasStatus) {
            return store.repository().findByTypeAndStatus(IDEA, status, page);
        }
        return store.page(IDEA, null, null, page);
    }

    private void requireVisible(AuthenticatedUser caller, StoredRecord idea) {
        if (caller.role() == Role.STUDENT) {
            requireOwner(caller, idea);
        }
    }

    private void requireOwner(AuthenticatedUser caller, StoredRecord record) {
        if (!caller.id().toString().equals(record.getOwnerId())) {
            throw new org.springframework.security.access.AccessDeniedException("This resource belongs to another user.");
        }
    }

    private ApiDtos.PageResponse<ApiDtos.IdeaResponse> pageResponse(Page<ApiDtos.IdeaResponse> records) {
        return new ApiDtos.PageResponse<>(records.getContent(), records.getNumber(), records.getSize(),
                records.getTotalElements(), records.getTotalPages());
    }
}
