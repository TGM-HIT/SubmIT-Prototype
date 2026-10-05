package com.flightlog.allocation;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Allocation")
public class AllocationController {
    private final AllocationService service;

    public AllocationController(AllocationService service) {
        this.service = service;
    }

    @PostMapping("/api/application-periods/{periodId}/allocation-runs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a replacement allocation after the deadline.")
    public ObjectNode start(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID periodId) {
        return service.startReplacement(caller, periodId);
    }

    @GetMapping("/api/application-periods/{periodId}/allocation-runs")
    public PageResponse<ObjectNode> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.listRuns(caller, periodId, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/api/allocation-runs/{id}")
    public ObjectNode get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return service.getRun(caller, id);
    }

    @PostMapping("/api/allocation-runs/{id}/finalize")
    public ObjectNode finalizeRun(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return service.finalizeRun(caller, id);
    }
}
