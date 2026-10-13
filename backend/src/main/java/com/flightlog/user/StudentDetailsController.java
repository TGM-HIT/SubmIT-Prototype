package com.flightlog.user;

import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.CatalogueEntryRequest;
import com.flightlog.web.ApiDtos.CatalogueEntryResponse;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiDtos.StudentDetailsRequest;
import com.flightlog.web.ApiDtos.StudentDetailsResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudentDetailsController {
    private static final int MAX_PAGE_SIZE = 100;
    private final StudentDetailsService details;

    public StudentDetailsController(StudentDetailsService details) {
        this.details = details;
    }

    @GetMapping("/api/student-classes")
    public PageResponse<CatalogueEntryResponse> classes(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return details.classes(caller, Math.max(page, 0), normalizeSize(size));
    }

    @PostMapping("/api/student-classes")
    public CatalogueEntryResponse createClass(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CatalogueEntryRequest request) {
        return details.createClass(caller, request);
    }

    @PatchMapping("/api/student-classes/{id}")
    public CatalogueEntryResponse renameClass(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody CatalogueEntryRequest request) {
        return details.renameClass(caller, id, request);
    }

    @GetMapping("/api/elective-subjects")
    public PageResponse<CatalogueEntryResponse> subjects(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "100") int size) {
        return details.subjects(caller, Math.max(page, 0), normalizeSize(size));
    }

    @PostMapping("/api/elective-subjects")
    public CatalogueEntryResponse createSubject(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CatalogueEntryRequest request) {
        return details.createSubject(caller, request);
    }

    @PatchMapping("/api/elective-subjects/{id}")
    public CatalogueEntryResponse renameSubject(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody CatalogueEntryRequest request) {
        return details.renameSubject(caller, id, request);
    }

    @GetMapping("/api/users/{id}/student-details")
    public StudentDetailsResponse details(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id) {
        return details.details(caller, id);
    }

    @PutMapping("/api/users/{id}/student-details")
    public StudentDetailsResponse assign(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id, @Valid @RequestBody StudentDetailsRequest request) {
        return details.assign(caller, id, request);
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
