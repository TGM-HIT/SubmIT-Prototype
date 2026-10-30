package at.ac.tgm.submit.archive;

import at.ac.tgm.submit.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Project artefacts and archive")
public class ProjectWorkController {
    private final ArtifactService service;

    public ProjectWorkController(ArtifactService service) {
        this.service = service;
    }

    @PostMapping(value = "/projects/{projectId}/artifacts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @ArtifactApiResponses.Upload
    public ArtifactResponse upload(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID projectId,
            @Valid @ModelAttribute ArtifactUploadRequest request) {
        return service.upload(caller, projectId, request);
    }

    @PostMapping("/projects/{projectId}/complete")
    @ArtifactApiResponses.Complete
    public ArtifactService.CompletionResponse complete(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID projectId) {
        return service.complete(caller, projectId);
    }
}
