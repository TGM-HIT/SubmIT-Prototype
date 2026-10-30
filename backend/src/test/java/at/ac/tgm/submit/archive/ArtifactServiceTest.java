package at.ac.tgm.submit.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.data.StoredRecordRepository;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import at.ac.tgm.submit.web.ApiException;
import jakarta.persistence.EntityNotFoundException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

@SpringBootTest
@WithMockUser(roles = "TEACHER")
class ArtifactServiceTest {
    private static final byte[] FILE_BYTES = "untrusted active html".getBytes(java.nio.charset.StandardCharsets.UTF_8);

    @Autowired
    private ArtifactService service;

    @Autowired
    private RecordStore store;

    @Autowired
    private StoredRecordRepository records;

    @BeforeEach
    void clearRecords() {
        records.deleteAll();
    }

    @Test
    void uploadStaysPrivateUntilTeacherApprovesAndProjectCompletes() {
        UUID projectId = createProject("IN_PROGRESS");
        AuthenticatedUser teacher = user(Role.TEACHER);
        ArtifactResponse uploaded = upload(teacher, projectId, ArtifactType.WEBSITE_ARCHIVE);

        assertEquals("PRIVATE", uploaded.publicationStatus());
        service.review(teacher, uploaded.id(), new PublicationReviewRequest("SUBMIT", "Reviewed snapshot"));
        service.review(teacher, uploaded.id(), new PublicationReviewRequest("APPROVE", "Redacted"));
        assertThrows(EntityNotFoundException.class, () -> service.publicArchive(projectId, 0, 20));
    }

    @Test
    void publicationDecisionIsReservedForTeachers() {
        UUID projectId = createProject("IN_PROGRESS");
        AuthenticatedUser teacher = user(Role.TEACHER);
        ArtifactResponse uploaded = upload(teacher, projectId, ArtifactType.POSTER);
        service.review(teacher, uploaded.id(), new PublicationReviewRequest("SUBMIT", null));

        assertThrows(AccessDeniedException.class, () -> service.review(user(Role.ADMIN), uploaded.id(),
                new PublicationReviewRequest("APPROVE", null)));
        assertThrows(AccessDeniedException.class, () -> service.review(user(Role.STUDENT), uploaded.id(),
                new PublicationReviewRequest("REJECT", null)));
    }

    @Test
    void studentMembershipMustBeVerifiableFromAllocationData() {
        UUID projectId = createProject("IN_PROGRESS");
        AuthenticatedUser student = user(Role.STUDENT);
        ArtifactResponse privateArtifact = upload(user(Role.TEACHER), projectId, ArtifactType.SOURCE_DATA);

        AccessDeniedException denied = assertThrows(AccessDeniedException.class,
                () -> upload(student, projectId, ArtifactType.SOURCE_DATA));
        assertTrue(denied.getMessage().contains("membership could not be verified"));
        assertThrows(AccessDeniedException.class, () -> service.get(student, privateArtifact.id()));

        ObjectNode allocation = store.object().put("projectId", projectId.toString());
        allocation.putArray("studentIds").add(student.id().toString());
        store.create("project_group", UUID.randomUUID().toString(), projectId.toString(), "ALLOCATED", allocation);

        assertEquals("PRIVATE", upload(student, projectId, ArtifactType.SOURCE_DATA).publicationStatus());
        assertEquals(FILE_BYTES.length, service.content(student, privateArtifact.id()).length);
    }

    @Test
    void completionRequiresFinalGradeAndEveryArtifactTypeAndPublicMetadataIsMinimal() {
        UUID projectId = createProject("IN_PROGRESS");
        AuthenticatedUser teacher = user(Role.TEACHER);
        assertThrows(ApiException.class, () -> service.complete(teacher, projectId));

        for (ArtifactType type : ArtifactType.values()) {
            ArtifactResponse artifact = upload(teacher, projectId, type);
            service.review(teacher, artifact.id(), new PublicationReviewRequest("SUBMIT", null));
            service.review(teacher, artifact.id(), new PublicationReviewRequest("APPROVE", "Safe for release"));
        }
        assertThrows(ApiException.class, () -> service.complete(teacher, projectId));
        store.create("final_grade", teacher.id().toString(), projectId.toString(), "CURRENT",
                store.object().put("projectId", projectId.toString()).put("grade", "A"));

        assertEquals("PROJECT_COMPLETED", service.complete(teacher, projectId).status());
        PageResponse<PublicArtifactResponse> archive = service.publicArchive(projectId, 0, 20);
        assertEquals(3, archive.totalElements());
        assertTrue(archive.content().stream().allMatch(item -> item.size() == FILE_BYTES.length));
        assertEquals(List.of("id", "type", "size"),
                java.util.Arrays.stream(PublicArtifactResponse.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName).toList());

        UUID websiteId = archive.content().stream()
                .filter(item -> item.type() == ArtifactType.WEBSITE_ARCHIVE).findFirst().orElseThrow().id();
        PublicArchiveController controller = new PublicArchiveController(service);
        var download = controller.content(projectId, websiteId);
        assertEquals(MediaType.APPLICATION_OCTET_STREAM, download.getHeaders().getContentType());
        assertTrue(download.getHeaders().getFirst("Content-Disposition").startsWith("attachment;"));
        assertEquals("nosniff", download.getHeaders().getFirst("X-Content-Type-Options"));
        assertEquals(FILE_BYTES.length, download.getBody().length);
    }

    private UUID createProject(String status) {
        StoredRecord project = store.create("project", null, null, status, store.object().put("title", "Demo"));
        store.create("formal_application", null, project.getId().toString(), "APPROVED", store.object());
        return project.getId();
    }

    private ArtifactResponse upload(AuthenticatedUser caller, UUID projectId, ArtifactType type) {
        ArtifactUploadRequest request = new ArtifactUploadRequest();
        request.setType(type);
        request.setTitle(type.name());
        request.setDescription("Public-safe description");
        request.setPublicationSubmissionMetadata("Contains private review notes.");
        request.setFile(new MockMultipartFile("file", "../private-index.html", MediaType.TEXT_HTML_VALUE, FILE_BYTES));
        return service.upload(caller, projectId, request);
    }

    private AuthenticatedUser user(Role role) {
        return new AuthenticatedUser(UUID.randomUUID(), role.name().toLowerCase(), role);
    }
}
