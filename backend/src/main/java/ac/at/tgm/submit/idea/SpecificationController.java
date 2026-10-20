package ac.at.tgm.submit.idea;

import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.web.ApiDtos.ReviewRequest;
import ac.at.tgm.submit.web.ApiDtos.ReviewResponse;
import ac.at.tgm.submit.web.ApiDtos.SpecificationRequest;
import ac.at.tgm.submit.web.ApiDtos.SpecificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ideas")
public class SpecificationController {
    private final IdeaService ideaService;
    private final ObjectMapper objectMapper;

    public SpecificationController(IdeaService ideaService, ObjectMapper objectMapper) {
        this.ideaService = ideaService;
        this.objectMapper = objectMapper;
    }

    @PutMapping("/{id}/specification")
    @Operation(summary = "Save a full specification after teacher approval of the idea.")
    public SpecificationResponse save(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody SpecificationRequest request) {
        return ideaService.saveSpecification(caller, id, objectMapper.valueToTree(request));
    }

    @PostMapping("/{id}/specification/submit")
    @Operation(summary = "Submit a saved idea specification for teacher review.")
    public SpecificationResponse submit(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return ideaService.submitSpecification(caller, id);
    }

    @PostMapping("/{id}/specification/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Review an idea specification and record its decision and feedback.")
    @IdeaApiResponses.TeacherAuthorSpecificationReview
    public ReviewResponse review(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody ReviewRequest request) {
        return ideaService.review(caller, id, true, objectMapper.valueToTree(request));
    }
}
