package ac.at.tgm.submit.formal;

import ac.at.tgm.submit.web.ApiDtos.ApiErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@ApiResponse(responseCode = "409",
        description = "The project's formal application is not approved.",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
public @interface ApprovalConflict {
}
