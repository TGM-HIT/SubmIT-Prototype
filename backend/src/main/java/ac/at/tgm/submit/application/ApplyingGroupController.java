package ac.at.tgm.submit.application;

import ac.at.tgm.submit.application.ApplyingGroupDtos.Action;
import ac.at.tgm.submit.application.ApplyingGroupDtos.ChangeRequest;
import ac.at.tgm.submit.application.ApplyingGroupDtos.GroupResponse;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@io.swagger.v3.oas.annotations.tags.Tag(name = "Applying groups")
@RequestMapping(value = "/api/application-periods/{periodId}/applying-groups", produces = "application/json")
public class ApplyingGroupController {
    private final ApplyingGroupService service;

    public ApplyingGroupController(ApplyingGroupService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @ApplyingGroupApiResponses.Create
    @Operation(operationId = "createApplyingGroup", summary = "Create a creator-only applying-group draft without replacing individual applications.")
    public GroupResponse create(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID periodId) {
        return service.create(caller, periodId);
    }

    @GetMapping
    @ApplyingGroupApiResponses.ListGroups
    @Operation(operationId = "listApplyingGroups", summary = "List your accepted groups and invitations, or authorized staff summaries.",
            description = "Unrelated students cannot read rosters or rankings. Page defaults to 20, maximum 100.")
    public PageResponse<GroupResponse> list(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID periodId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(caller, periodId, Math.max(0, page), Math.min(100, Math.max(1, size)));
    }

    @GetMapping("/{id}")
    @ApplyingGroupApiResponses.GetGroup
    @Operation(operationId = "getApplyingGroup", summary = "Read a roster and shared ranking for informed consent; members, invitees and staff only.")
    public GroupResponse get(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID periodId, @PathVariable UUID id) {
        return service.get(caller, periodId, id);
    }

    @GetMapping("/{id}/invitations")
    @ApplyingGroupApiResponses.Invitations
    @Operation(operationId = "listApplyingGroupInvitations", summary = "List pending invitations for the current creator or authorized staff.",
            description = "Other members and invitees cannot read other pending invitations. Default size 20, maximum 100.")
    public PageResponse<ApplyingGroupDtos.MemberResponse> invitations(@AuthenticationPrincipal AuthenticatedUser caller,
            @PathVariable UUID periodId, @PathVariable UUID id, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.invitations(caller, periodId, id, Math.max(0, page), Math.min(100, Math.max(1, size)));
    }

    @PostMapping("/{id}/actions/{action}")
    @ApplyingGroupApiResponses.Change
    @Operation(operationId = "changeApplyingGroup", summary = "Change a shared application with version-bound consent.",
            description = "Students only, open period before deadline. Creator manages invitations/ranking/submission/withdrawal/"
                    + "transfer/dissolution; invitees accept/decline; accepted members approve themselves or leave. "
                    + "Every request supplies the current revision; stale requests conflict. Actual roster/ranking changes "
                    + "invalidate all approvals and reopen submissions, never restoring solo applications. SUBMIT and "
                    + "DISSOLVE require confirm=true. Submission requires 2–5 accepted students, three distinct catalogue "
                    + "preferences and unanimous approval of that revision; solo replacement is atomic.")
    public GroupResponse change(@AuthenticationPrincipal AuthenticatedUser caller, @PathVariable UUID periodId,
            @PathVariable UUID id, @PathVariable Action action, @Valid @RequestBody ChangeRequest request) {
        return service.change(caller, periodId, id, action, request);
    }
}
