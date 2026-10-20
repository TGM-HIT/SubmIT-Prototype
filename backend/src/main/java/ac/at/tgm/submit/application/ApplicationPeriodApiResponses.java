package ac.at.tgm.submit.application;

import ac.at.tgm.submit.web.ApiDtos.ApiErrorResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

final class ApplicationPeriodApiResponses {
    private ApplicationPeriodApiResponses() {
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @Tag(name = "School years and applications")
    @interface SchoolYearsAndApplications {
    }

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OK", useReturnTypeSchema = true),
            @ApiResponse(responseCode = "409", description = "The proposed catalogue removes a project that has an "
                    + "allocation pin.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    @interface PinnedCatalogueUpdate {
    }
}
