package com.flightlog.idea;

import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ideas")
class IdeaListController {
    private static final int MAX_PAGE_SIZE = 100;
    private final IdeaListService ideaList;

    IdeaListController(IdeaListService ideaList) {
        this.ideaList = ideaList;
    }

    @GetMapping
    @Operation(summary = "List visible ideas filtered by status, exact school year, and owner.")
    ApiDtos.PageResponse<ApiDtos.IdeaResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @ParameterObject IdeaListFilters filters,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; values are normalized to the inclusive range 1–100.")
            @RequestParam(defaultValue = "20") int size) {
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ideaList.list(caller, filters, Math.max(page, 0), normalizedSize);
    }
}
