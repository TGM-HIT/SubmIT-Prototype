package com.flightlog.idea;

import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "List ideas visible to the authenticated user, optionally filtered by status.")
    ApiDtos.PageResponse<ApiDtos.IdeaResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int normalizedSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return ideaList.list(caller, status, Math.max(page, 0), normalizedSize);
    }
}
