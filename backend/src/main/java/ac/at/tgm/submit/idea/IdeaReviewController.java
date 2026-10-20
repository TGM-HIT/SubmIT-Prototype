package ac.at.tgm.submit.idea;

import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.web.ApiDtos.IdeaReviewRequest;
import ac.at.tgm.submit.web.ApiDtos.ReviewResponse;
import ac.at.tgm.submit.web.ApiDtos.SupervisionPreferenceRequest;
import ac.at.tgm.submit.web.ApiDtos.SupervisionPreferenceResponse;
import ac.at.tgm.submit.web.ApiDtos.SupervisorRequest;
import ac.at.tgm.submit.web.ApiDtos.SupervisorResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdeaReviewController {
    private final IdeaService ideaService;
    private final SupervisionService supervisionService;
    private final ObjectMapper objectMapper;

    public IdeaReviewController(IdeaService ideaService, SupervisionService supervisionService,
            ObjectMapper objectMapper) {
        this.ideaService = ideaService;
        this.supervisionService = supervisionService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/ideas/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    @IdeaApiResponses.TeacherAuthorIdeaReview
    public ReviewResponse review(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody IdeaReviewRequest request) {
        return ideaService.review(caller, id, false, objectMapper.valueToTree(request));
    }

    @PutMapping("/api/ideas/{id}/supervision-preference")
    @IdeaApiResponses.SupervisionPreference
    public SupervisionPreferenceResponse setSupervisionPreference(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody SupervisionPreferenceRequest request) {
        return supervisionService.setPreference(caller, id, objectMapper.valueToTree(request));
    }

    @PutMapping("/api/ideas/{id}/supervisor")
    @IdeaApiResponses.SupervisorAssignment
    public SupervisorResponse assignSupervisor(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody SupervisorRequest request) {
        return supervisionService.assign(caller, id, objectMapper.valueToTree(request));
    }
}
