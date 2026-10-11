package com.flightlog.idea;

import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.IdeaOwnerRequest;
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
@RequestMapping("/api/ideas/{id}/owners")
class IdeaOwnershipController {
    private final IdeaOwnership ownership;

    IdeaOwnershipController(IdeaOwnership ownership) {
        this.ownership = ownership;
    }

    @PostMapping
    public com.flightlog.web.ApiDtos.IdeaResponse add(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody IdeaOwnerRequest request) {
        return ownership.addOwner(caller, id, request.studentId());
    }

    @DeleteMapping("/{studentId}")
    public com.flightlog.web.ApiDtos.IdeaResponse remove(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @PathVariable UUID studentId) {
        return ownership.removeOwner(caller, id, studentId);
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        ownership.leave(caller, id);
    }
}
