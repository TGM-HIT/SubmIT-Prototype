package com.flightlog.user;

import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.CatalogueEntryRequest;
import com.flightlog.web.ApiDtos.CatalogueEntryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "student-details-controller")
public class StudentCatalogueCreationController {
    private final StudentDetailsService details;

    public StudentCatalogueCreationController(StudentDetailsService details) {
        this.details = details;
    }

    @PostMapping("/api/student-classes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a shared student-class catalogue entry.")
    @ApiResponse(responseCode = "201", description = "Catalogue entry created.",
            content = @Content(schema = @Schema(implementation = CatalogueEntryResponse.class)))
    public CatalogueEntryResponse createClass(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CatalogueEntryRequest request) {
        return details.createClass(caller, request);
    }

    @PostMapping("/api/elective-subjects")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a shared elective-subject catalogue entry.")
    @ApiResponse(responseCode = "201", description = "Catalogue entry created.",
            content = @Content(schema = @Schema(implementation = CatalogueEntryResponse.class)))
    public CatalogueEntryResponse createSubject(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody CatalogueEntryRequest request) {
        return details.createSubject(caller, request);
    }
}
