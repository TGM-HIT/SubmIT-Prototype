package at.ac.tgm.submit.audit;

import at.ac.tgm.submit.security.AuthenticatedUser;
import at.ac.tgm.submit.web.ApiDtos.AuditEntryResponse;
import at.ac.tgm.submit.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    @Operation(summary = "List audit events visible to the authenticated user.")
    public PageResponse<AuditEntryResponse> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; values are normalized to the inclusive range 1–100.")
            @RequestParam(defaultValue = "20") int size) {
        return auditService.list(caller, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/{entityType}/{entityId}")
    @Operation(summary = "List audit events for an entity visible to the authenticated user.")
    public PageResponse<AuditEntryResponse> forEntity(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable String entityType, @PathVariable UUID entityId,
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size; values are normalized to the inclusive range 1–100.")
            @RequestParam(defaultValue = "20") int size) {
        return auditService.forEntity(caller, entityType, entityId, Math.max(page, 0),
                Math.min(Math.max(size, 1), 100));
    }
}
