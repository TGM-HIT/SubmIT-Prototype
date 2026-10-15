package com.flightlog.allocation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import com.flightlog.web.ApiDtos.AllocationRunPageResponse;
import com.flightlog.web.ApiDtos.AllocationRunResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
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
    private final ObjectMapper objectMapper;

    public AllocationController(AllocationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/api/application-periods/{periodId}/allocation-runs")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Start a replacement allocation after the deadline.")
    @ApiResponse(responseCode = "201", description = "Allocation run started.",
            content = @Content(schema = @Schema(implementation = AllocationRunResponse.class)))
    public Map<String, Object> start(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID periodId) {
        return plain(service.startReplacement(caller, periodId));
    }

    @GetMapping("/api/application-periods/{periodId}/allocation-runs")
    @ApiResponse(responseCode = "200", description = "Paginated allocation runs.",
            content = @Content(schema = @Schema(implementation = AllocationRunPageResponse.class)))
    public PageResponse<Map<String, Object>> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PageResponse<ObjectNode> runs = service.listRuns(caller, periodId,
                Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return new PageResponse<>(runs.content().stream().map(this::plain).toList(), runs.page(), runs.size(),
                runs.totalElements(), runs.totalPages());
    }

    @GetMapping("/api/allocation-runs/{id}")
    @ApiResponse(responseCode = "200", description = "Allocation run with its result snapshot.",
            content = @Content(schema = @Schema(implementation = AllocationRunResponse.class)))
    public Map<String, Object> get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return plain(service.getRun(caller, id));
    }

    @PostMapping("/api/allocation-runs/{id}/finalize")
    public Map<String, Object> finalizeRun(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID id) {
        return plain(service.finalizeRun(caller, id));
    }

    private Map<String, Object> plain(ObjectNode response) {
        return objectMapper.convertValue(response, new TypeReference<>() { });
    }
}
