package ac.at.tgm.submit.archive;

import ac.at.tgm.submit.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/artifacts/{artifactId}")
@Tag(name = "Project artefacts and archive")
public class ArtifactReviewController {
    private final ArtifactService service;

    public ArtifactReviewController(ArtifactService service) {
        this.service = service;
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove an artefact before project completion.")
    @ArtifactApiResponses.Delete
    public void delete(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID artifactId) {
        service.delete(caller, artifactId);
    }

    @PostMapping("/publication-review")
    @Operation(summary = "Submit an artefact for publication review or record a teacher decision.")
    @ArtifactApiResponses.PublicationReview
    public ArtifactResponse review(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID artifactId, @Valid @RequestBody PublicationReviewRequest request) {
        return service.review(caller, artifactId, request);
    }
}
