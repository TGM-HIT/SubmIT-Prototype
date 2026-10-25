package ac.at.tgm.submit.archive;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.audit.AuditService;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.formal.FormalApprovalGate;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtifactService {
    private static final String ARTIFACT = "artifact";
    private static final String PRIVATE = "PRIVATE";
    private static final String PENDING_REVIEW = "PENDING_REVIEW";
    private static final String APPROVED = "APPROVED";
    private static final String PROJECT_COMPLETED = "PROJECT_COMPLETED";
    private static final String REJECTED = "REJECTED";
    private static final Set<String> ACTIVE_PROJECT_STATUSES = Set.of("ACCEPTED_FOR_APPLICATIONS", "IN_PROGRESS",
            "PROJECT_IN_PROGRESS", "ACTIVE", "ONGOING");
    private static final Set<String> COMPLETED_PROJECT_STATUSES = Set.of("COMPLETED", PROJECT_COMPLETED);
    private static final Set<String> REQUIRED_TYPES = Set.of("WEBSITE_ARCHIVE", "POSTER", "SOURCE_DATA");
    private static final int PAGE_SCAN_SIZE = 100;
    private final RecordStore store;
    private final AuditService audit;
    private final ArtifactProjectAccess projectAccess;
    private final ArtifactPayloadMapper mapper;

    public ArtifactService(RecordStore store, AuditService audit, ArtifactProjectAccess projectAccess,
            ArtifactPayloadMapper mapper) {
        this.store = store;
        this.audit = audit;
        this.projectAccess = projectAccess;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public PageResponse<ArtifactResponse> list(AuthenticatedUser caller, UUID projectId, int page, int size) {
        projectAccess.findProject(projectId);
        projectAccess.requireProjectAccess(caller, projectId);
        Page<ArtifactResponse> results = store.page(ARTIFACT, null, projectId.toString(),
                PageRequest.of(page, size)).map(mapper::response);
        return pageResponse(results);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ArtifactResponse upload(AuthenticatedUser caller, UUID projectId, ArtifactUploadRequest request) {
        StoredRecord project = projectAccess.findProject(projectId);
        requireActiveProject(project);
        projectAccess.requireProjectAccess(caller, projectId);
        mapper.validateUpload(request);
        FormalApprovalGate.requireApproved(store, projectId);
        ObjectNode payload = mapper.uploadPayload(request);
        StoredRecord artifact = store.create(ARTIFACT, caller.id().toString(), projectId.toString(), PRIVATE, payload);
        audit.record(caller, "ARTIFACT_UPLOADED", ARTIFACT, artifact.getId());
        return mapper.response(artifact);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public ArtifactResponse get(AuthenticatedUser caller, UUID artifactId) {
        StoredRecord artifact = projectAccess.findArtifact(artifactId);
        UUID projectId = projectId(artifact);
        projectAccess.findProject(projectId);
        projectAccess.requireProjectAccess(caller, projectId);
        return mapper.response(artifact);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("isAuthenticated()")
    public byte[] content(AuthenticatedUser caller, UUID artifactId) {
        StoredRecord artifact = projectAccess.findArtifact(artifactId);
        UUID projectId = projectId(artifact);
        projectAccess.findProject(projectId);
        projectAccess.requireProjectAccess(caller, projectId);
        return mapper.decodeContent(artifact);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public void delete(AuthenticatedUser caller, UUID artifactId) {
        StoredRecord artifact = projectAccess.findArtifact(artifactId);
        UUID projectId = projectId(artifact);
        StoredRecord project = projectAccess.findProject(projectId);
        requireActiveProject(project);
        projectAccess.requireProjectAccess(caller, projectId);
        if (caller.role() == Role.STUDENT && !caller.id().equals(parseUuid(artifact.getOwnerId()))) {
            throw new AccessDeniedException("Only the artefact uploader or a teacher can remove it.");
        }
        if (APPROVED.equals(artifactStatus(artifact))) {
            throw ArtifactErrors.conflict("Unpublish the artefact before removing it.");
        }
        FormalApprovalGate.requireApproved(store, projectId);
        audit.record(caller, "ARTIFACT_REMOVED", ARTIFACT, artifactId);
        store.delete(artifact);
    }

    @Transactional
    @PreAuthorize("isAuthenticated()")
    public ArtifactResponse review(AuthenticatedUser caller, UUID artifactId, PublicationReviewRequest request) {
        StoredRecord artifact = projectAccess.findArtifact(artifactId);
        UUID projectId = projectId(artifact);
        StoredRecord project = projectAccess.findProject(projectId);
        requireActiveProject(project);
        projectAccess.requireProjectAccess(caller, projectId);
        String decision = request.decision();
        if ("APPROVE".equals(decision) || "REJECT".equals(decision) || "UNPUBLISH".equals(decision)) {
            requireTeacher(caller);
        }
        String nextStatus = reviewStatus(artifactStatus(artifact), decision);
        FormalApprovalGate.requireApproved(store, projectId);
        ObjectNode payload = store.parse(artifact);
        payload.put("publicationStatus", nextStatus);
        payload.put("reviewedAt", Instant.now().toString());
        if (request.note() != null && !request.note().isBlank()) {
            payload.put("reviewNote", request.note().trim());
        } else {
            payload.remove("reviewNote");
        }
        store.update(artifact, nextStatus, payload);
        audit.record(caller, "ARTIFACT_PUBLICATION_" + decision, ARTIFACT, artifactId);
        return mapper.response(artifact);
    }

    @Transactional(readOnly = true)
    public PageResponse<PublicArtifactResponse> publicArchive(UUID projectId, int page, int size) {
        requireCompletedProject(projectAccess.findProject(projectId));
        List<PublicArtifactResponse> content = new ArrayList<>();
        long total = 0;
        long first = (long) page * size;
        long last = first + size;
        Page<StoredRecord> batch = store.page(ARTIFACT, null, projectId.toString(),
                PageRequest.of(0, PAGE_SCAN_SIZE));
        int scanPage = 0;
        while (true) {
            for (StoredRecord artifact : batch.getContent()) {
                if (APPROVED.equals(artifactStatus(artifact))) {
                    if (total >= first && total < last) {
                        content.add(mapper.publicResponse(artifact));
                    }
                    total++;
                }
            }
            if (!batch.hasNext()) {
                break;
            }
            batch = store.page(ARTIFACT, null, projectId.toString(),
                    PageRequest.of(++scanPage, PAGE_SCAN_SIZE));
        }
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(content, page, size, total, totalPages);
    }

    @Transactional(readOnly = true)
    public byte[] publicContent(UUID projectId, UUID artifactId) {
        requireCompletedProject(projectAccess.findProject(projectId));
        StoredRecord artifact = projectAccess.findArtifact(artifactId);
        if (!projectId.toString().equals(artifact.getParentId()) || !APPROVED.equals(artifactStatus(artifact))) {
            throw new EntityNotFoundException("Public artefact was not found.");
        }
        return mapper.decodeContent(artifact);
    }

    @Transactional
    @PreAuthorize("hasRole('TEACHER')")
    public CompletionResponse complete(AuthenticatedUser caller, UUID projectId) {
        requireTeacher(caller);
        StoredRecord project = projectAccess.findProject(projectId);
        requireActiveProject(project);
        if (!hasFinalGrade(projectId)) {
            throw ArtifactErrors.conflict("A final-grade record is required before project completion.");
        }
        Set<String> uploadedTypes = uploadedTypes(projectId);
        if (!uploadedTypes.containsAll(REQUIRED_TYPES)) {
            throw ArtifactErrors.conflict(
                    "A website archive, poster, and source data artefact are required before completion.");
        }
        FormalApprovalGate.requireApproved(store, projectId);
        ObjectNode payload = store.parse(project);
        payload.put("completedAt", Instant.now().toString());
        store.update(project, PROJECT_COMPLETED, payload);
        audit.record(caller, "PROJECT_COMPLETED", "project", projectId);
        return new CompletionResponse(projectId, PROJECT_COMPLETED, payload.path("completedAt").asText());
    }

    private String reviewStatus(String current, String decision) {
        return switch (decision) {
            case "SUBMIT" -> {
                requireStatus(current, Set.of(PRIVATE, REJECTED), "Only a private or rejected artefact can be submitted for review.");
                yield PENDING_REVIEW;
            }
            case "APPROVE", "REJECT" -> {
                requireStatus(current, Set.of(PENDING_REVIEW), "Only an artefact awaiting review can be approved or rejected.");
                yield "APPROVE".equals(decision) ? APPROVED : REJECTED;
            }
            case "UNPUBLISH" -> {
                requireStatus(current, Set.of(APPROVED), "Only a published artefact can be unpublished.");
                yield PRIVATE;
            }
            default -> throw ArtifactErrors.badRequest("Unsupported publication review decision.");
        };
    }

    private void requireStatus(String current, Set<String> expected, String message) {
        if (!expected.contains(current)) {
            throw ArtifactErrors.conflict(message);
        }
    }

    private boolean hasFinalGrade(UUID projectId) {
        for (String type : List.of("final_grade", "project_final_grade")) {
            Page<StoredRecord> batch = store.page(type, null, null, PageRequest.of(0, PAGE_SCAN_SIZE));
            int page = 0;
            while (true) {
                if (batch.getContent().stream().anyMatch(record ->
                        "CURRENT".equals(record.getStatus()) && projectMatches(record, projectId))) {
                    return true;
                }
                if (!batch.hasNext()) {
                    break;
                }
                batch = store.page(type, null, null, PageRequest.of(++page, PAGE_SCAN_SIZE));
            }
        }
        return false;
    }

    private boolean projectMatches(StoredRecord record, UUID projectId) {
        return projectId.toString().equals(record.getParentId())
                || projectId.toString().equals(store.parse(record).path("projectId").asText());
    }

    private Set<String> uploadedTypes(UUID projectId) {
        Set<String> types = new java.util.HashSet<>();
        Page<StoredRecord> batch = store.page(ARTIFACT, null, projectId.toString(),
                PageRequest.of(0, PAGE_SCAN_SIZE));
        int page = 0;
        while (true) {
            batch.getContent().forEach(artifact -> types.add(store.parse(artifact).path("type").asText()));
            if (types.containsAll(REQUIRED_TYPES) || !batch.hasNext()) {
                return types;
            }
            batch = store.page(ARTIFACT, null, projectId.toString(),
                    PageRequest.of(++page, PAGE_SCAN_SIZE));
        }
    }

    private UUID projectId(StoredRecord artifact) {
        return parseUuid(artifact.getParentId());
    }

    private UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new EntityNotFoundException("Project was not found.");
        }
    }

    private void requireActiveProject(StoredRecord project) {
        if (!ACTIVE_PROJECT_STATUSES.contains(project.getStatus())) {
            throw ArtifactErrors.conflict(
                    "Artefacts cannot be changed after project completion or before project work starts.");
        }
    }

    private void requireCompletedProject(StoredRecord project) {
        if (!COMPLETED_PROJECT_STATUSES.contains(project.getStatus())) {
            throw new EntityNotFoundException("Public archive was not found.");
        }
    }

    private void requireTeacher(AuthenticatedUser caller) {
        if (caller.role() != Role.TEACHER) {
            throw new AccessDeniedException("Only a teacher may make publication or completion decisions.");
        }
    }

    private String artifactStatus(StoredRecord artifact) {
        return store.parse(artifact).path("publicationStatus").asText(artifact.getStatus());
    }

    private <T> PageResponse<T> pageResponse(Page<T> records) {
        return new PageResponse<>(records.getContent(), records.getNumber(), records.getSize(),
                records.getTotalElements(), records.getTotalPages());
    }

    @Schema(requiredProperties = {"projectId", "status", "completedAt"})
    public record CompletionResponse(
            @Schema(description = "Completed project ID.") UUID projectId,
            @Schema(description = "Project completion status.") String status,
            @Schema(description = "Completion instant.") String completedAt) {
    }
}
