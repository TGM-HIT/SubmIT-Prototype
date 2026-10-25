package ac.at.tgm.submit.archive;

import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Project artefacts and archive")
public class ArtifactController {
    private static final int MAX_PAGE_SIZE = 100;

    private final ArtifactService service;

    public ArtifactController(ArtifactService service) {
        this.service = service;
    }

    @GetMapping("/projects/{projectId}/artifacts")
    @Operation(summary = "List paginated project artefacts visible to the caller.")
    public PageResponse<ArtifactResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID projectId, @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; values are normalized to the inclusive range 1–100.")
            @RequestParam(defaultValue = "20") int size) {
        return service.list(caller, projectId, Math.max(page, 0), normalizeSize(size));
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
