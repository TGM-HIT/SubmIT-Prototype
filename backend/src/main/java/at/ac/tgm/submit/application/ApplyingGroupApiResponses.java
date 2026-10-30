package at.ac.tgm.submit.application;

import at.ac.tgm.submit.web.ApiDtos.ApiErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

final class ApplyingGroupApiResponses {
    private ApplyingGroupApiResponses() {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "201", description = "Creator-only draft; individual submissions remain eligible.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Invalid period UUID or caller account is no longer a student.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a student.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Application period was not found.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Period is not open before its deadline, or caller already has accepted membership.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @interface Create {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "200", description = "Paginated authorized groups and invitations.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Invalid period UUID or pagination query parameter.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Application period was not found.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @interface ListGroups {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "200", description = "Authorized roster and ranking.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Invalid period or group UUID.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Student caller is neither an accepted member nor a pending invitee.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Applying group was not found in this period, or its owning period is missing.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @interface GetGroup {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "200", description = "Paginated pending invitations.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Invalid period/group UUID or pagination query parameter.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Student caller is not the current creator.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Applying group was not found in this period.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @interface Invitations {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponse(responseCode = "200", description = "Updated shared application and current revision.", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "400", description = "Invalid period/group UUID, action enum, JSON body, revision, member/project "
            + "UUID, minimum ranking length, current student role, distinctness or catalogue membership; "
            + "a self-only action specifies another student.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "403", description = "Caller is not a student, the current creator for a management action, "
            + "the invited student for acceptance/decline, or an accepted member for approval/departure.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Period or applying group in this period was not found.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "Period is locked; revision is stale or group dissolved; target, confirmation "
            + "or ranking is missing; duplicate invitation, membership uniqueness/size, catalogue acceptance, unanimous "
            + "consent, an ineligible accepted member, creator departure, conflicting effective pins or effective capacity "
            + "prevents the transition.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    @interface Change {
    }
}
