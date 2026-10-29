package ac.at.tgm.submit.idea;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.web.ApiDtos;
import ac.at.tgm.submit.web.ApiException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IdeaPdfService {
    private static final String IDEA = "idea";
    private static final String SPECIFICATION = "specification";
    private static final String IDEA_DRAFT = "IDEA_DRAFT";
    private static final String IDEA_APPROVED = "IDEA_APPROVED_FOR_SPECIFICATION";
    private static final String SPEC_NEEDS_REVISION = "SPECIFICATION_NEEDS_REVISION";
    private static final String SPEC_DRAFT = "SPECIFICATION_DRAFT";
    private static final String WITHDRAWN = "WITHDRAWN";
    private static final String PDF_CONTENT = IdeaService.PDF_CONTENT;
    private static final String REVISION_ID = IdeaService.REVISION_ID;

    private final RecordStore store;
    private final IdeaService ideaService;
    private final PdfFileValidator pdfValidator;

    public IdeaPdfService(RecordStore store, IdeaService ideaService, PdfFileValidator pdfValidator) {
        this.store = store;
        this.ideaService = ideaService;
        this.pdfValidator = pdfValidator;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ApiDtos.IdeaResponse create(AuthenticatedUser caller, ApiDtos.IdeaPdfMetadataRequest metadata,
            MultipartFile file) {
        byte[] bytes = pdfValidator.validate(file);
        ObjectNode payload = store.object();
        payload.put("title", metadata.title().trim());
        payload.put("schoolYear", metadata.schoolYear().trim());
        payload.put("description", "");
        payload.put("intendedOutcome", "");
        payload.set("scopePoints", store.value(List.of()));
        payload.set("learningTopics", store.value(List.of()));
        payload.set("collaboratorIds", store.value(metadata.collaboratorIds() == null
                ? List.of() : metadata.collaboratorIds()));
        ObjectNode storedPayload = ideaService.preparePdfIdeaCreate(payload, caller);
        storedPayload.put(PDF_CONTENT, Base64.getEncoder().encodeToString(bytes));
        StoredRecord record = store.create(IDEA, caller.id().toString(), null, IDEA_DRAFT, storedPayload);
        ideaService.recordPdfIdeaCreated(caller, record);
        return ideaService.ideaResponse(record, caller.role());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ApiDtos.IdeaResponse update(AuthenticatedUser caller, UUID id, ApiDtos.IdeaPdfMetadataRequest metadata,
            MultipartFile file) {
        byte[] bytes = pdfValidator.validate(file);
        StoredRecord idea = ideaService.findIdea(id);
        ideaService.validatePdfIdeaUpdate(caller, idea);
        ObjectNode payload = store.parse(idea);
        payload.put("title", metadata.title().trim());
        payload.put("schoolYear", metadata.schoolYear().trim());
        payload.put(PDF_CONTENT, Base64.getEncoder().encodeToString(bytes));
        store.update(idea, WITHDRAWN.equals(idea.getStatus()) ? IDEA_DRAFT : idea.getStatus(), payload);
        ideaService.recordPdfIdeaUpdated(caller, id);
        return ideaService.ideaResponse(idea, caller.role());
    }

    @Transactional
    @PreAuthorize("hasAnyRole('STUDENT', 'TEACHER')")
    public ApiDtos.SpecificationResponse saveSpecification(AuthenticatedUser caller, UUID id, MultipartFile file) {
        byte[] bytes = pdfValidator.validate(file);
        StoredRecord idea = ideaService.findIdea(id);
        StoredRecord current = store.repository()
                .findFirstByTypeAndParentIdOrderByCreatedAtDesc(SPECIFICATION, id.toString()).orElse(null);
        ideaService.validatePdfSpecificationUpdate(caller, idea, current);
        ObjectNode payload = emptySpecificationPayload(bytes);
        StoredRecord revision = store.create("specification_revision", caller.id().toString(), id.toString(),
                "SAVED", payload.deepCopy());
        payload.put(REVISION_ID, revision.getId().toString());
        if (current == null) {
            current = store.create(SPECIFICATION, caller.id().toString(), id.toString(), SPEC_DRAFT, payload);
        } else {
            store.update(current, SPEC_NEEDS_REVISION, payload);
        }
        if (WITHDRAWN.equals(idea.getStatus())) {
            store.update(idea, IDEA_APPROVED, store.parse(idea));
        }
        ideaService.recordPdfSpecificationUpdated(caller, current);
        return ideaService.specificationResponse(current);
    }

    @Transactional(readOnly = true)
    public byte[] read(AuthenticatedUser caller, UUID id, boolean specification) {
        StoredRecord idea = ideaService.findIdea(id);
        if (caller.role() == Role.STUDENT) {
            ideaService.requireOwner(caller, idea);
        }
        StoredRecord document = specification
                ? store.repository().findFirstByTypeAndParentIdOrderByCreatedAtDesc(SPECIFICATION, id.toString())
                        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PDF document was not found."))
                : idea;
        String encoded = store.parse(document).path(PDF_CONTENT).asText(null);
        if (encoded == null || encoded.isBlank()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PDF document was not found.");
        }
        try {
            return Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Persisted PDF data is invalid.", exception);
        }
    }

    private ObjectNode emptySpecificationPayload(byte[] bytes) {
        ObjectNode payload = store.object();
        payload.set("objectives", store.value(List.of()));
        payload.set("intendedUsers", store.value(List.of()));
        payload.set("requirements", store.value(List.of()));
        payload.set("deliverables", store.value(List.of()));
        payload.put("scope", "");
        payload.set("dependencies", store.value(List.of()));
        payload.put(PDF_CONTENT, Base64.getEncoder().encodeToString(bytes));
        return payload;
    }
}
