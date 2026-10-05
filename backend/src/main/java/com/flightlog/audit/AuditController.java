package com.flightlog.audit;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.PageResponse;
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
    public PageResponse<ObjectNode> list(@AuthenticationPrincipal AuthenticatedUser caller,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return auditService.list(caller, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/{entityType}/{entityId}")
    public PageResponse<ObjectNode> forEntity(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable String entityType, @PathVariable UUID entityId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return auditService.forEntity(caller, entityType, entityId, Math.max(page, 0),
                Math.min(Math.max(size, 1), 100));
    }
}
