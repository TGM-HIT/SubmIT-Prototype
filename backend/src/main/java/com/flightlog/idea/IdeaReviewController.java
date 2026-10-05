package com.flightlog.idea;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.ReviewRequest;
import com.flightlog.web.ApiDtos.ReviewResponse;
import com.flightlog.web.ApiDtos.SupervisionPreferenceRequest;
import com.flightlog.web.ApiDtos.SupervisionPreferenceResponse;
import com.flightlog.web.ApiDtos.SupervisorRequest;
import com.flightlog.web.ApiDtos.SupervisorResponse;
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

    @PostMapping("/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse review(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody ReviewRequest request) {
        return ideaService.review(caller, id, false, objectMapper.valueToTree(request));
    }

    @PutMapping("/{id}/supervision-preference")
    public SupervisionPreferenceResponse setSupervisionPreference(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody SupervisionPreferenceRequest request) {
        return supervisionService.setPreference(caller, id, objectMapper.valueToTree(request));
    }

    @PutMapping("/{id}/supervisor")
    public SupervisorResponse assignSupervisor(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody SupervisorRequest request) {
        return supervisionService.assign(caller, id, objectMapper.valueToTree(request));
    }
}
