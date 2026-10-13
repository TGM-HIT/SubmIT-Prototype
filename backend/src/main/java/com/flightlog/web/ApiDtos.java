package com.flightlog.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.flightlog.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 80) String username,
            @NotBlank @Size(min = 12, max = 128) String password,
            @NotNull @Schema(description = "Required account name; trimmed before storage and limited to 120 "
                    + "characters after trimming.")
            String displayName,
            Role role) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record TokenResponse(String token, String tokenType, long expiresIn, UserResponse user) {
    }

    public record UserResponse(UUID id, String username,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName, Role role,
            @Schema(description = "Current student class; returned only on the administrator account-list endpoint.")
            @JsonInclude(JsonInclude.Include.NON_NULL) String className,
            @Schema(description = "Current elective subject names; returned only on the administrator account-list endpoint.")
            @JsonInclude(JsonInclude.Include.NON_NULL) List<String> electiveSubjectNames) {
    }

    public record DisplayNameRequest(
            @NotNull @Schema(description = "Required account name; leading and trailing whitespace is trimmed. "
                    + "The trimmed name must contain 1 to 120 characters.")
            String displayName) {
    }

    public record RoleRequest(@NotNull Role role) {
    }

    public record CatalogueEntryRequest(
            @NotBlank @Size(min = 1, max = 100)
            @Schema(description = "Unique shared class or elective-subject name; trimmed before storage; 1 to 100 characters.",
                    minLength = 1, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
            String name) {
    }

    public record CatalogueEntryResponse(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID id,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {
    }

    public record StudentDetailsRequest(
            @NotNull @Schema(description = "Required ID of the students single assigned class.") UUID classId,
            @NotNull @Size(max = 4, message = "A student can have at most four elective subjects.")
            @ArraySchema(arraySchema = @Schema(description =
                    "Assigned shared elective subject IDs; required, may be empty, IDs must be unique, maximum four."),
                    maxItems = 4, uniqueItems = true, schema = @Schema(format = "uuid"))
            List<@NotNull UUID> electiveSubjectIds) {
    }

    public record StudentDetailsResponse(
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID studentId,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uuid") UUID classId,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String className,
            @NotNull @ArraySchema(arraySchema = @Schema(requiredMode = Schema.RequiredMode.REQUIRED),
                    schema = @Schema(format = "uuid")) List<UUID> electiveSubjectIds,
            @NotNull @ArraySchema(arraySchema = @Schema(
                    description = "Current catalogue names, returned only to authenticated teachers and administrators.",
                    requiredMode = Schema.RequiredMode.REQUIRED)) List<String> electiveSubjectNames) {
    }

    public record ApiErrorResponse(@NotNull int status, @NotNull String error, @NotNull String message) {
    }

    public record IdeaRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotBlank @Size(max = 2000) String intendedOutcome,
            @NotEmpty List<@NotBlank @Size(max = 1000) String> scopePoints,
            @Size(max = 50) List<@NotBlank @Size(max = 200) String> learningTopics,
            @NotBlank @Size(max = 20) String schoolYear,
            @Size(max = 4, message = "An idea can have at most five owners including its creator.") List<@NotNull UUID> collaboratorIds) {
    }

    public record SpecificationRequest(
            @NotEmpty List<@NotBlank @Size(max = 2000) String> objectives,
            @NotEmpty List<@NotBlank @Size(max = 1000) String> intendedUsers,
            @NotEmpty List<@NotBlank @Size(max = 2000) String> requirements,
            @NotEmpty List<@NotBlank @Size(max = 1000) String> deliverables,
            @NotBlank @Size(max = 5000) String scope,
            @NotEmpty List<@NotBlank @Size(max = 1000) String> dependencies) {
    }

    public record ReviewRequest(
            @NotBlank String decision,
            @NotBlank @Size(max = 5000) String feedback,
            @Min(1) @Max(5) Integer workSize,
            @Min(1) @Max(5) Integer curriculumRelevance,
            @Min(1) @Max(5) Integer technicalComplexity,
            @Min(1) @Max(5) Integer feasibility,
            @Min(1) @Max(5) Integer learningValue,
            @Min(1) @Max(5) Integer resourceNeeds,
            @Min(1) @Max(5) Integer riskAndDependencies,
            @Size(max = 2000) String recommendation) {
    }

    /**
     * Idea review request: ratings may be omitted, but a rating that is present as an explicit JSON null is
     * rejected. Bean properties are used because Jackson only applies the null check to supplied properties.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IdeaReviewRequest {
        @NotBlank
        private String decision;
        @NotBlank
        @Size(max = 5000)
        private String feedback;
        @Min(1)
        @Max(5)
        private Integer workSize;
        @Min(1)
        @Max(5)
        private Integer curriculumRelevance;
        @Min(1)
        @Max(5)
        private Integer technicalComplexity;
        @Min(1)
        @Max(5)
        private Integer feasibility;
        @Min(1)
        @Max(5)
        private Integer learningValue;
        @Min(1)
        @Max(5)
        private Integer resourceNeeds;
        @Min(1)
        @Max(5)
        private Integer riskAndDependencies;
        @Size(max = 2000)
        private String recommendation;

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            this.decision = decision;
        }

        public String getFeedback() {
            return feedback;
        }

        public void setFeedback(String feedback) {
            this.feedback = feedback;
        }

        public Integer getWorkSize() {
            return workSize;
        }

        @JsonSetter(nulls = Nulls.FAIL)
        public void setWorkSize(Integer workSize) {
            this.workSize = workSize;
        }

        public Integer getCurriculumRelevance() {
            return curriculumRelevance;
        }

        @JsonSetter(nulls = Nulls.FAIL)
        public void setCurriculumRelevance(Integer curriculumRelevance) {
            this.curriculumRelevance = curriculumRelevance;
        }

        public Integer getTechnicalComplexity() {
            return technicalComplexity;
        }

        @JsonSetter(nulls = Nulls.FAIL)
        public void setTechnicalComplexity(Integer technicalComplexity) {
            this.technicalComplexity = technicalComplexity;
        }

        public Integer getFeasibility() {
            return feasibility;
        }

        @JsonSetter(nulls = Nulls.FAIL)
        public void setFeasibility(Integer feasibility) {
            this.feasibility = feasibility;
        }

        public Integer getLearningValue() {
            return learningValue;
        }

        @JsonSetter(nulls = Nulls.FAIL)
        public void setLearningValue(Integer learningValue) {
            this.learningValue = learningValue;
        }

        public Integer getResourceNeeds() {
            return resourceNeeds;
        }

        @JsonSetter(nulls = Nulls.FAIL)
        public void setResourceNeeds(Integer resourceNeeds) {
            this.resourceNeeds = resourceNeeds;
        }

        public Integer getRiskAndDependencies() {
            return riskAndDependencies;
        }

        @JsonSetter(nulls = Nulls.FAIL)
        public void setRiskAndDependencies(Integer riskAndDependencies) {
            this.riskAndDependencies = riskAndDependencies;
        }

        public String getRecommendation() {
            return recommendation;
        }

        public void setRecommendation(String recommendation) {
            this.recommendation = recommendation;
        }
    }

    public record SupervisionPreferenceRequest(
            @NotBlank @Schema(allowableValues = {"WANTS_TO_SUPERVISE", "WILLING_TO_SUPERVISE",
                    "NOT_WILLING_TO_SUPERVISE", "NOT_AVAILABLE"})
            String preference,
            @Size(max = 2000) String note) {
    }

    public record SupervisorRequest(
            @NotNull UUID teacherId,
            @Schema(description = "Explicitly acknowledge that this teacher recorded an explicit refusal "
                    + "(NOT_WILLING_TO_SUPERVISE or NOT_AVAILABLE). Required to assign such a teacher.")
            Boolean acknowledgeRefusal) {
    }

    public record ApplicationPeriodRequest(
            @NotBlank @Size(min = 1, max = 100)
            @Schema(minLength = 1, maxLength = 100,
                    description = "Required administrator-set period label; trimmed, non-blank after trimming, "
                            + "and at most 100 characters. Blank or whitespace-only values are rejected.")
            String label,
            @NotBlank @Size(max = 20) String schoolYear,
            @NotNull Instant opensAt,
            @NotNull Instant closesAt,
            @NotEmpty List<@NotNull UUID> projectIds) {
    }

    public record StudentApplicationRequest(@NotEmpty List<@NotNull UUID> projectIds) {
    }

    public record IndividualTopic(@NotNull UUID studentId,
            @NotBlank @Size(max = 200) String topic) {
    }

    public record StudentText(@NotNull UUID studentId,
            @NotBlank @Size(max = 2400) String text) {
    }

    public record Milestone(@NotBlank @Size(max = 1000) String text, @NotNull LocalDate date) {
    }

    public record FormalApplicationRequest(
            @NotBlank @Size(max = 200) String topic,
            @NotEmpty List<@Valid IndividualTopic> individualTopics,
            @NotBlank @Size(max = 400) String initialSituation,
            @NotEmpty List<@Valid StudentText> investigationConcerns,
            @NotBlank @Size(max = 400) String objective,
            @NotEmpty List<@Valid StudentText> plannedResults,
            @NotEmpty List<@Valid Milestone> milestones,
            @Size(max = 1000) String cooperationPartner) {
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    public record SpecificationResponse(
            UUID id,
            String status,
            List<String> objectives,
            List<String> intendedUsers,
            List<String> requirements,
            List<String> deliverables,
            String scope,
            List<String> dependencies,
            String revisionId) {
    }

    public record ReviewResponse(
            UUID id,
            UUID teacherId,
            @Schema(description = "Current account display name; included only in authorized idea history.")
            String teacherDisplayName,
            String reviewType,
            String decision,
            String feedback,
            Integer workSize,
            Integer curriculumRelevance,
            Integer technicalComplexity,
            Integer feasibility,
            Integer learningValue,
            Integer resourceNeeds,
            Integer riskAndDependencies,
            String recommendation) {
    }

    public record SupervisionPreferenceResponse(
            UUID id, UUID teacherId,
            @Schema(description = "Current account display name; included only in authorized idea history.")
            String teacherDisplayName,
            String preference, String note) {
    }

    public record SupervisorResponse(UUID id, UUID teacherId, String status) {
    }

    public record IdeaOwnerResponse(UUID id, String displayName, boolean creator,
            @Schema(description = "Current class name; only included in authenticated teacher and administrator responses.")
            @JsonInclude(JsonInclude.Include.NON_NULL) String className,
            @Schema(description = "Current elective subject names; only included in authenticated teacher and administrator responses.")
            @JsonInclude(JsonInclude.Include.NON_NULL) List<String> electiveSubjectNames) {
    }

    public record StudentLookupResponse(UUID id, String displayName) {
    }

    public record IdeaOwnerRequest(@NotNull UUID studentId) {
    }

    public record IdeaResponse(
            UUID id,
            UUID ownerId,
            UUID creatorId,
            List<IdeaOwnerResponse> owners,
            @Schema(description = "Current account display name; included only in authorized idea responses.")
            String ownerDisplayName,
            String status,
            String title,
            String description,
            String intendedOutcome,
            List<String> scopePoints,
            List<String> learningTopics,
            String schoolYear,
            @JsonInclude(JsonInclude.Include.NON_NULL) String confirmedSupervisorDisplayName,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Whether the current author account is a teacher; included only in staff responses.")
            Boolean teacherIdea,
            SpecificationResponse specification,
            List<ReviewResponse> reviews,
            List<SupervisionPreferenceResponse> supervisionPreferences) {
    }

    public record ApplicationPeriodResponse(
            UUID id,
            @NotNull
            @Schema(maxLength = 100,
                    description = "Period label; legacy records without a label are presented using their "
                            + "school year. At most 100 characters.")
            String label,
            String schoolYear,
            Instant opensAt,
            Instant closesAt,
            String status,
            List<UUID> projectIds,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<ProjectTitleResponse> projectTitles,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Present only to the authenticated student; status of that student's application, or NOT_SUBMITTED.")
            String studentApplicationStatus,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Present only for the authenticated student's own submitted application.")
            StudentAssignmentResponse studentAssignment) {
    }

    public record CatalogueSpecificationResponse(
            List<String> objectives,
            List<String> intendedUsers,
            List<String> requirements,
            List<String> deliverables,
            String scope,
            List<String> dependencies) {
    }

    public record CatalogueProjectResponse(
            UUID id,
            String title,
            String description,
            String intendedOutcome,
            List<String> scopePoints,
            List<String> learningTopics,
            String schoolYear,
            CatalogueSpecificationResponse specification) {
    }

    public record ProjectTitleResponse(UUID projectId, String title) {
    }

    public record PreferenceResponse(UUID projectId, int rank, String projectTitle) {
    }

    public record StudentAssignmentResponse(
            @Schema(description = "NO_RESULT, PRELIMINARY, FINAL, NOT_ASSIGNED_PRELIMINARY, or NOT_ASSIGNED_FINAL.",
                    allowableValues = {"NO_RESULT", "PRELIMINARY", "FINAL", "NOT_ASSIGNED_PRELIMINARY",
                            "NOT_ASSIGNED_FINAL"})
            String state,
            @Schema(description = "Current project title; present only when assigned.") String projectTitle,
            @Schema(description = "The caller's rank for the assigned project, when present in their application.")
            Integer preferenceRank,
            @Schema(description = "Current display names of this assigned project's group only; contains no IDs.")
            List<StudentGroupMemberResponse> groupMembers) {
    }

    public record StudentGroupMemberResponse(
            @Schema(description = "Current account display name, or Unknown student.") String displayName,
            @Schema(description = "True only for the authenticated caller.") boolean self) {
    }

    public record StudentApplicationResponse(
            UUID id,
            UUID studentId,
            String studentDisplayName,
            UUID periodId,
            String status,
            List<PreferenceResponse> preferences,
            @Schema(description = "Current class name; only included in authenticated teacher and administrator responses.")
            @JsonInclude(JsonInclude.Include.NON_NULL) String className,
            @Schema(description = "Current elective subject names; only included in authenticated teacher and administrator responses.")
            @JsonInclude(JsonInclude.Include.NON_NULL) List<String> electiveSubjectNames) {
    }

    public record FormalReviewResponse(UUID id, UUID teacherId, String decision, String feedback) {
    }

    public record FormalApplicationResponse(
            UUID id,
            UUID projectId,
            String status,
            String topic,
            List<IndividualTopic> individualTopics,
            String initialSituation,
            List<StudentText> investigationConcerns,
            String objective,
            List<StudentText> plannedResults,
            List<Milestone> milestones,
            String cooperationPartner,
            PageResponse<FormalReviewResponse> reviewHistory) {
    }
}
