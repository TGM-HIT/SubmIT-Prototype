package at.ac.tgm.submit.idea;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.audit.AuditService;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.web.ApiDtos;
import at.ac.tgm.submit.web.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdeaService {
    static final String PDF_CONTENT = "privatePdfBase64";
    static final String REVISION_ID = "revisionId";

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
    private static final String WITHDRAWN = "WITHDRAWN";
    private static final String LEARNING_TOPICS = "learningTopics";

    private record ReviewContext(AuthenticatedUser caller, UUID ideaId, StoredRecord idea,
            boolean specificationReview, String nextStatus) {
    }

    private final RecordStore store;
    private final AuditService audit;
    private final IdeaResponseMapper mapper;
    private final IdeaRules rules;
    private final IdeaOwnership ownership;

    public IdeaService(RecordStore store, AuditService audit, IdeaResponseMapper mapper, IdeaRules rules,
            IdeaOwnership ownership) {
        this.store = store;
        this.audit = audit;
        this.mapper = mapper;
        this.rules = rules;
        this.ownership = ownership;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ApiDtos.IdeaResponse create(AuthenticatedUser caller, JsonNode request) {
        ObjectNode payload = rules.object(request);
        validateIdeaPayload(payload);
        ObjectNode storedPayload = ownership.prepareCreate(payload, caller.id(), caller.role());
        StoredRecord record = store.create(IDEA, caller.id().toString(), null, IDEA_DRAFT, storedPayload);
        audit.record(caller, "IDEA_CREATED", IDEA, record.getId());
        ownership.ownerIds(record).stream().filter(ownerId -> !ownerId.equals(caller.id()))
                .forEach(ownerId -> audit.record(caller, "IDEA_OWNER_ADDED", IDEA, record.getId()));
        return mapper.response(record, false, caller.role());
    }

    @Transactional(readOnly = true)
    public ApiDtos.IdeaResponse get(AuthenticatedUser caller, UUID id) {
        StoredRecord idea = findIdea(id);
        requireVisible(caller, idea);
        return mapper.response(idea, true, caller.role());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ApiDtos.IdeaResponse update(AuthenticatedUser caller, UUID id, JsonNode request) {
        StoredRecord idea = findIdea(id);
        requireOwner(caller, idea);
        rules.status(idea, IDEA_DRAFT, IDEA_NEEDS_REVISION, WITHDRAWN);
        rejectGenericLifecycleForWithdrawnSpecification(idea);
        ObjectNode payload = rules.object(request);
        validateIdeaPayload(payload);
        ownership.preserveOwners(idea, payload);
        store.update(idea, WITHDRAWN.equals(idea.getStatus()) ? IDEA_DRAFT : idea.getStatus(), payload);
        audit.record(caller, "IDEA_UPDATED", IDEA, id);
        return mapper.response(idea, false, caller.role());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ApiDtos.IdeaResponse submit(AuthenticatedUser caller, UUID id) {
        StoredRecord idea = findIdea(id);
        requireOwner(caller, idea);
        rules.status(idea, IDEA_DRAFT, IDEA_NEEDS_REVISION, WITHDRAWN);
        rejectGenericLifecycleForWithdrawnSpecification(idea);
        ObjectNode payload = store.parse(idea);
        validateIdeaPayload(payload);
        store.update(idea, IDEA_SUBMITTED, payload);
        audit.record(caller, "IDEA_SUBMITTED", IDEA, id);
        return mapper.response(idea, false, caller.role());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ApiDtos.SpecificationResponse saveSpecification(AuthenticatedUser caller, UUID id, JsonNode request) {
        StoredRecord idea = findIdea(id);
        requireOwner(caller, idea);
        rules.status(idea, IDEA_APPROVED, SPEC_NEEDS_REVISION, WITHDRAWN);
        ObjectNode payload = rules.object(request);
        rules.nonEmptyArray(payload, "objectives");
        rules.nonEmptyArray(payload, "intendedUsers");
        rules.nonEmptyArray(payload, "requirements");
        rules.nonEmptyArray(payload, "deliverables");
        rules.text(payload, "scope");
        rules.nonEmptyArray(payload, "dependencies");
        StoredRecord current = store.repository()
                .findFirstByTypeAndParentIdOrderByCreatedAtDesc(SPECIFICATION, id.toString()).orElse(null);
        ensureSpecificationEditable(idea, current);
        StoredRecord revision = store.create("specification_revision", caller.id().toString(), id.toString(),
                "SAVED", payload.deepCopy());
        payload.put(REVISION_ID, revision.getId().toString());
        if (current == null) {
            current = store.create(SPECIFICATION, caller.id().toString(), id.toString(),
                    "SPECIFICATION_DRAFT", payload);
        } else {
            store.update(current, SPEC_NEEDS_REVISION, payload);
        }
        if (WITHDRAWN.equals(idea.getStatus())) {
            store.update(idea, IDEA_APPROVED, store.parse(idea));
        }
        audit.record(caller, "SPECIFICATION_UPDATED", SPECIFICATION, current.getId());
        return mapper.specificationResponse(current);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
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
        if (caller.id().toString().equals(idea.getOwnerId())) {
            throw new org.springframework.security.access.AccessDeniedException("Authors cannot review their own ideas.");
        }
        ObjectNode payload = rules.object(request);
        String decision = rules.text(payload, "decision");
        rules.text(payload, "feedback");
        rules.ratings(payload);
        payload.put("reviewType", specificationReview ? "SPECIFICATION" : "IDEA");
        String expectedStatus = specificationReview ? SPEC_SUBMITTED : IDEA_SUBMITTED;
        if (!expectedStatus.equals(idea.getStatus())) {
            throw rules.conflict("The requested decision is not valid at this lifecycle stage.");
        }
        ReviewContext context = new ReviewContext(caller, id, idea, specificationReview,
                rules.nextReviewStatus(decision, specificationReview));
        return persistReview(context, decision, payload);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ApiDtos.IdeaResponse withdraw(AuthenticatedUser caller, UUID id) {
        StoredRecord idea = findIdea(id);
        requireOwner(caller, idea);
        rules.status(idea, IDEA_DRAFT, IDEA_SUBMITTED, IDEA_NEEDS_REVISION, IDEA_APPROVED,
                SPEC_SUBMITTED, SPEC_NEEDS_REVISION);
        store.update(idea, "WITHDRAWN", store.parse(idea));
        audit.record(caller, "IDEA_WITHDRAWN", IDEA, id);
        return mapper.response(idea, false, caller.role());
    }

    public ApiDtos.IdeaResponse addOwner(AuthenticatedUser caller, UUID id, UUID studentId) {
        return ownership.addOwner(caller, id, studentId);
    }

    public ApiDtos.IdeaResponse removeOwner(AuthenticatedUser caller, UUID id, UUID studentId) {
        return ownership.removeOwner(caller, id, studentId);
    }

    public void leave(AuthenticatedUser caller, UUID id) {
        ownership.leave(caller, id);
    }

    ObjectNode preparePdfIdeaCreate(ObjectNode payload, AuthenticatedUser caller) {
        return ownership.prepareCreate(payload, caller.id(), caller.role());
    }

    void recordPdfIdeaCreated(AuthenticatedUser caller, StoredRecord record) {
        audit.record(caller, "IDEA_CREATED", IDEA, record.getId());
        ownership.ownerIds(record).stream().filter(ownerId -> !ownerId.equals(caller.id()))
                .forEach(ownerId -> audit.record(caller, "IDEA_OWNER_ADDED", IDEA, record.getId()));
    }

    void validatePdfIdeaUpdate(AuthenticatedUser caller, StoredRecord idea) {
        requireOwner(caller, idea);
        rules.status(idea, IDEA_DRAFT, IDEA_NEEDS_REVISION, WITHDRAWN);
        rejectGenericLifecycleForWithdrawnSpecification(idea);
    }

    void recordPdfIdeaUpdated(AuthenticatedUser caller, UUID id) {
        audit.record(caller, "IDEA_UPDATED", IDEA, id);
    }

    void validatePdfSpecificationUpdate(AuthenticatedUser caller, StoredRecord idea,
            StoredRecord specification) {
        requireOwner(caller, idea);
        rules.status(idea, IDEA_APPROVED, SPEC_NEEDS_REVISION, WITHDRAWN);
        ensureSpecificationEditable(idea, specification);
    }

    void recordPdfSpecificationUpdated(AuthenticatedUser caller, StoredRecord specification) {
        audit.record(caller, "SPECIFICATION_UPDATED", SPECIFICATION, specification.getId());
    }

    ApiDtos.IdeaResponse ideaResponse(StoredRecord record, Role viewerRole) {
        return mapper.response(record, false, viewerRole);
    }

    ApiDtos.SpecificationResponse specificationResponse(StoredRecord record) {
        return mapper.specificationResponse(record);
    }

    private ApiDtos.ReviewResponse persistReview(ReviewContext context, String decision, ObjectNode payload) {
        AuthenticatedUser caller = context.caller();
        UUID id = context.ideaId();
        boolean specificationReview = context.specificationReview();
        StoredRecord review = store.create(REVIEW, caller.id().toString(), id.toString(), decision, payload);
        if (specificationReview) {
            createSpecificationReviewSnapshot(caller, id, review);
        }
        store.update(context.idea(), context.nextStatus(), store.parse(context.idea()));
        if (specificationReview) {
            updateReviewedSpecification(id, context.nextStatus());
        }
        audit.record(caller, "IDEA_REVIEW_RECORDED", IDEA, review.getId());
        return mapper.reviewResponse(review);
    }

    private void updateReviewedSpecification(UUID id, String nextStatus) {
        StoredRecord specification = store.repository()
                .findFirstByTypeAndParentIdOrderByCreatedAtDesc(SPECIFICATION, id.toString())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "A full specification is required."));
        store.update(specification, nextStatus, store.parse(specification));
    }

    private void createSpecificationReviewSnapshot(AuthenticatedUser caller, UUID id, StoredRecord review) {
        StoredRecord specification = store.repository()
                .findFirstByTypeAndParentIdOrderByCreatedAtDesc(SPECIFICATION, id.toString())
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "A full specification is required."));
        ObjectNode reviewed = store.parse(specification);
        ObjectNode snapshot = store.object();
        snapshot.put(REVISION_ID, reviewed.path(REVISION_ID).asText(""));
        if (reviewed.has(PDF_CONTENT)) {
            snapshot.set(PDF_CONTENT, reviewed.get(PDF_CONTENT).deepCopy());
        }
        store.create("specification_review_snapshot", caller.id().toString(), review.getId().toString(),
                "SUBMITTED_REVISION", snapshot);
    }

    private void validateIdeaPayload(ObjectNode payload) {
        rules.text(payload, "title");
        rules.text(payload, "schoolYear");
        if (payload.has(PDF_CONTENT)) {
            return;
        }
        rules.text(payload, "description");
        rules.text(payload, "intendedOutcome");
        rules.nonEmptyArray(payload, "scopePoints");
        rules.optionalArray(payload, LEARNING_TOPICS);
    }

    void ensureSpecificationEditable(StoredRecord idea, StoredRecord specification) {
        if (specification == null || SPEC_DRAFT.equals(specification.getStatus())
                || SPEC_NEEDS_REVISION.equals(specification.getStatus())) {
            return;
        }
        if (WITHDRAWN.equals(idea.getStatus()) && SPEC_SUBMITTED.equals(specification.getStatus())) {
            store.update(specification, SPEC_DRAFT, store.parse(specification));
            return;
        }
        throw rules.conflict("A submitted specification cannot be edited until returned for revision.");
    }

    void rejectGenericLifecycleForWithdrawnSpecification(StoredRecord idea) {
        if (!WITHDRAWN.equals(idea.getStatus())) {
            return;
        }
        StoredRecord specification = store.repository()
                .findFirstByTypeAndParentIdOrderByCreatedAtDesc(SPECIFICATION, idea.getId().toString())
                .orElse(null);
        if (specification != null && (SPEC_SUBMITTED.equals(specification.getStatus())
                || SPEC_NEEDS_REVISION.equals(specification.getStatus())
                || SPEC_DRAFT.equals(specification.getStatus()))) {
            throw rules.conflict("A withdrawn full specification must be edited or submitted through the specification flow.");
        }
    }

    StoredRecord findIdea(UUID id) {
        return store.repository().findById(id).filter(record -> IDEA.equals(record.getType()))
                .orElseThrow(() -> new ApiException(org.springframework.http.HttpStatus.NOT_FOUND,
                        "Idea was not found."));
    }

    private void requireVisible(AuthenticatedUser caller, StoredRecord idea) {
        if (caller.role() == Role.STUDENT) {
            requireOwner(caller, idea);
        }
    }

    void requireOwner(AuthenticatedUser caller, StoredRecord record) {
        if (!ownership.ownerIds(record).contains(caller.id())) {
            throw new org.springframework.security.access.AccessDeniedException("This resource belongs to another user.");
        }
    }

}
