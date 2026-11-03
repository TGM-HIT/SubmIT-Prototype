package at.ac.tgm.submit.archive;

import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.user.UserAccount;
import at.ac.tgm.submit.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/** Opt-in disposable public-archive fixture for the local testing preview only. */
@Configuration
@Profile("testing & !testing-tgm")
@ConditionalOnProperty(name = "dadb.testing.archive-preview", havingValue = "true")
public class TestingArchivePreviewConfiguration {
    private static final String TEACHER_USERNAME = "teacher01";
    private static final int FIXTURE_PAGE_SIZE = 20;
    private final RecordStore store;
    private final UserAccountRepository users;
    private final ArtifactService artifacts;

    public TestingArchivePreviewConfiguration(RecordStore store, UserAccountRepository users,
            ArtifactService artifacts) {
        this.store = store;
        this.users = users;
        this.artifacts = artifacts;
    }

    @Bean
    @Order(5)
    ApplicationRunner archivePreview() {
        return arguments -> {
            try {
                UserAccount teacherAccount = users.findByUsername(TEACHER_USERNAME).orElseThrow();
                AuthenticatedUser teacher = new AuthenticatedUser(teacherAccount.getId(), TEACHER_USERNAME, Role.TEACHER);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        teacher, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))));
                var project = store.create("project", teacherAccount.getId().toString(), null, "IN_PROGRESS",
                        store.object().put("title", "Reviewed website archive preview"));
                store.create("formal_application", teacherAccount.getId().toString(), project.getId().toString(),
                        "APPROVED", store.object().put("projectId", project.getId().toString()));
                store.create("final_grade", teacherAccount.getId().toString(), project.getId().toString(),
                        "CURRENT", store.object().put("projectId", project.getId().toString())
                                .put("grade", "A").put("rationale", "Synthetic preview prerequisite."));
                uploadAndApprove(teacher, project.getId(), ArtifactType.WEBSITE_ARCHIVE, createWaczFixture());
                uploadAndApprove(teacher, project.getId(), ArtifactType.POSTER, "%PDF-1.4 preview".getBytes(StandardCharsets.UTF_8));
                uploadAndApprove(teacher, project.getId(), ArtifactType.SOURCE_DATA,
                        "Synthetic preview source data.".getBytes(StandardCharsets.UTF_8));
                artifacts.complete(teacher, project.getId());
                UUID websiteArtifact = artifacts.publicArchive(project.getId(), 0, FIXTURE_PAGE_SIZE).content().stream()
                        .filter(item -> item.type() == ArtifactType.WEBSITE_ARCHIVE)
                        .map(PublicArtifactResponse::id).findFirst().orElseThrow();
                store.create("testing_archive_preview", null, project.getId().toString(), "SEEDED",
                        store.object().put("projectId", project.getId().toString())
                                .put("websiteArtifactId", websiteArtifact.toString()));
                System.out.println("ARCHIVE_PREVIEW_PROJECT_ID=" + project.getId());
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private void uploadAndApprove(AuthenticatedUser teacher, UUID projectId, ArtifactType type, byte[] bytes) {
        ArtifactUploadRequest request = new ArtifactUploadRequest();
        request.setType(type);
        request.setTitle("Reviewed " + type.name().toLowerCase(java.util.Locale.ROOT));
        request.setDescription("Synthetic opt-in preview fixture.");
        request.setFile(new TestingPreviewFile(type.name().toLowerCase(java.util.Locale.ROOT) + ".wacz",
                type == ArtifactType.WEBSITE_ARCHIVE ? "application/wacz" : "application/octet-stream", bytes));
        ArtifactResponse uploaded = artifacts.upload(teacher, projectId, request);
        artifacts.review(teacher, uploaded.id(), new PublicationReviewRequest("SUBMIT", null));
        artifacts.review(teacher, uploaded.id(), new PublicationReviewRequest("APPROVE", null));
    }

    static byte[] createWaczFixture() {
        return TestingWaczFixture.create();
    }
}
