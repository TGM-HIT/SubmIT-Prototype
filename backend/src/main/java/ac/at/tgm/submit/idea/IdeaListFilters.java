package ac.at.tgm.submit.idea;

import io.swagger.v3.oas.annotations.Parameter;
import java.util.UUID;

record IdeaListFilters(
        @Parameter(description = "Exact status filter; absent or blank values do not restrict results.")
        String status,
        @Parameter(description = "Exact-match school year filter.")
        String schoolYear,
        @Parameter(description = "Owner UUID; matches the creator or any collaborator.")
        UUID ownerId) {
}
