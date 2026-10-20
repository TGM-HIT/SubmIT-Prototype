package ac.at.tgm.submit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.web.ApiDtos.ApplicationPeriodResponse;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import ac.at.tgm.submit.web.ApiDtos.ApplicationPeriodRequest;
import ac.at.tgm.submit.web.ApiDtos.CatalogueProjectResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApplicationPeriodApiResponses.SchoolYearsAndApplications
public class ApplicationPeriodController {
    private static final int MAX_PAGE_SIZE = 100;

    private final ApplicationPeriodService service;
    private final ObjectMapper objectMapper;

    public ApplicationPeriodController(ApplicationPeriodService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/api/school-years")
    @Operation(summary = "List available school years.")
    public PageResponse<String> schoolYears(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.schoolYears(Math.max(page, 0), normalizeSize(size));
    }

    @PostMapping("/api/application-periods")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create an application period with its project catalogue.")
    public ApplicationPeriodResponse createPeriod(@AuthenticationPrincipal AuthenticatedUser caller,
            @Valid @RequestBody ApplicationPeriodRequest request) {
        return service.create(caller, objectMapper.valueToTree(request));
    }

    @GetMapping("/api/application-periods")
    @Operation(summary = "List application periods.")
    public PageResponse<ApplicationPeriodResponse> listPeriods(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(caller, Math.max(page, 0), normalizeSize(size));
    }

    @GetMapping("/api/application-periods/{id}")
    @Operation(summary = "Get an application period and its current status.")
    public ApplicationPeriodResponse getPeriod(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id) {
        return service.get(caller, id);
    }

    @GetMapping("/api/application-periods/{periodId}/catalogue/{projectId}")
    @Operation(summary = "Get a project's period-specific catalogue details.")
    public CatalogueProjectResponse getCatalogueProject(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId, @PathVariable UUID projectId) {
        return service.getCatalogueProject(caller, periodId, projectId);
    }

    @PutMapping("/api/application-periods/{id}")
    @Operation(summary = "Update an application period and its project catalogue.")
    @ApplicationPeriodApiResponses.PinnedCatalogueUpdate
    public ApplicationPeriodResponse updatePeriod(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID id,
            @Valid @RequestBody ApplicationPeriodRequest request) {
        return service.update(caller, id, objectMapper.valueToTree(request));
    }

    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }
}
