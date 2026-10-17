package com.flightlog.idea;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.IdeaResponse;
import com.flightlog.web.ApiDtos.IdeaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ideas")
@Tag(name = "Ideas and reviews")
public class IdeaController {
    private static final int MAX_PAGE_SIZE = 100;

    private final IdeaService ideaService;
    private final ObjectMapper objectMapper;

    public IdeaController(IdeaService ideaService, ObjectMapper objectMapper) {
        this.ideaService = ideaService;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an idea draft for the authenticated student or teacher.")
    public IdeaResponse create(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody IdeaRequest request) {
        return ideaService.create(caller, objectMapper.valueToTree(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an idea and its authorized review and ownership details.")
    public IdeaResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return ideaService.get(caller, id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an idea draft, returned idea, or withdrawn idea owned by the authenticated author.")
    public IdeaResponse update(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id,
            @Valid @RequestBody IdeaRequest request) {
        return ideaService.update(caller, id, objectMapper.valueToTree(request));
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit an owned draft, returned idea, or withdrawn idea for teacher review.")
    public IdeaResponse submit(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return ideaService.submit(caller, id);
    }

    @PostMapping("/{id}/withdraw")
    @Operation(summary = "Withdraw an owned idea from teacher review.")
    public IdeaResponse withdraw(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return ideaService.withdraw(caller, id);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
