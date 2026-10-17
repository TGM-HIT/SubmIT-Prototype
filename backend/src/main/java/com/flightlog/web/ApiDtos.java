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
            @NotBlank @Size(min = 3, max = 80)
            @Schema(description = "Required login name; 3 to 80 characters.")
            String username,
            @NotBlank @Size(min = 12, max = 128)
            @Schema(description = "Required password; 12 to 128 characters.")
            String password,
            @NotNull @Schema(description = "Required account name; trimmed before storage and limited to 120 "
                    + "characters after trimming.")
            String displayName,
            Role role) {
    }

    public record LoginRequest(
            @NotBlank @Schema(description = "Required login name; at least one non-whitespace character.",
                    minLength = 1) String username,
            @NotBlank @Schema(description = "Required password; at least one non-whitespace character.",
                    minLength = 1) String password) {
    }

    public record TokenResponse(String token, String tokenType, long expiresIn, UserResponse user) {
    }

    public record UserResponse(UUID id, String username,
            @Schema(description = "Current account display name.", requiredMode = Schema.RequiredMode.REQUIRED)
            String displayName, Role role,
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

    public record RoleRequest(
            @NotNull @Schema(description = "Account role to assign.", allowableValues = {"STUDENT", "TEACHER", "ADMIN"})
            Role role) {
    }

    public record CatalogueEntryRequest(
            @NotBlank @Size(min = 1, max = 100)
            @Schema(description = "Unique case-insensitively shared class or elective-subject name; trimmed before storage; "
                    + "1 to 100 characters.",
                    minLength = 1, maxLength = 100, requiredMode = Schema.RequiredMode.REQUIRED)
            String name) {
    }

    public record CatalogueEntryResponse(
            @NotNull @Schema(description = "Catalogue entry ID.", requiredMode = Schema.RequiredMode.REQUIRED,
                    format = "uuid") UUID id,
            @NotNull @Schema(description = "Current catalogue entry name.", requiredMode = Schema.RequiredMode.REQUIRED)
            String name) {
    }

    public record StudentDetailsRequest(
            @NotNull @Schema(description = "Required ID of the student's single assigned class.", format = "uuid")
            UUID classId,
            @NotNull @Size(max = 4, message = "A student can have at most four elective subjects.")
            @ArraySchema(arraySchema = @Schema(description =
                    "Assigned shared elective subject IDs; required, may be empty, IDs must be unique, maximum four."),
                    maxItems = 4, uniqueItems = true, schema = @Schema(format = "uuid"))
            List<@NotNull UUID> electiveSubjectIds) {
    }

    public record StudentDetailsResponse(
            @NotNull @Schema(description = "ID of the student.", requiredMode = Schema.RequiredMode.REQUIRED,
                    format = "uuid") UUID studentId,
            @NotNull @Schema(description = "Current student display name.", requiredMode = Schema.RequiredMode.REQUIRED)
            String displayName,
            @NotNull @Schema(description = "ID of the student's assigned class.", requiredMode = Schema.RequiredMode.REQUIRED,
                    format = "uuid") UUID classId,
            @NotNull @Schema(description = "Current name of the student's assigned class.",
                    requiredMode = Schema.RequiredMode.REQUIRED) String className,
            @NotNull @ArraySchema(arraySchema = @Schema(description = "IDs of assigned elective subjects.",
                    requiredMode = Schema.RequiredMode.REQUIRED),
                    schema = @Schema(format = "uuid")) List<UUID> electiveSubjectIds,
            @NotNull @ArraySchema(arraySchema = @Schema(
                    description = "Current catalogue names, returned only to authenticated teachers and administrators.",
                    requiredMode = Schema.RequiredMode.REQUIRED)) List<String> electiveSubjectNames) {
    }

    public record ApiErrorResponse(
            @NotNull @Schema(description = "HTTP response status code.") int status,
            @NotNull @Schema(description = "Short error category.") String error,
            @NotNull @Schema(description = "Human-readable error detail.") String message) {
    }

    public record IdeaRequest(
            @NotBlank @Size(max = 200) @Schema(description = "Required, non-blank idea title; at most 200 characters.",
                    minLength = 1)
            String title,
            @NotBlank @Size(max = 5000) @Schema(description = "Required, non-blank idea description; at most 5,000 characters.",
                    minLength = 1)
            String description,
            @NotBlank @Size(max = 2000) @Schema(description = "Required, non-blank intended outcome; at most 2,000 characters.",
                    minLength = 1)
            String intendedOutcome,
            @NotEmpty @ArraySchema(arraySchema = @Schema(description = "At least one non-blank scope point; each is at most 1,000 characters."),
                    minItems = 1, schema = @Schema(description = "Non-blank scope point; at most 1,000 characters.",
                            minLength = 1))
            List<@NotBlank @Size(max = 1000) String> scopePoints,
            @Size(max = 50) @ArraySchema(arraySchema = @Schema(description = "Optional learning topics; at most 50 entries."),
                    maxItems = 50, schema = @Schema(description = "Non-blank learning topic; at most 200 characters.",
                            minLength = 1))
            List<@NotBlank @Size(max = 200) String> learningTopics,
            @NotBlank @Schema(description = "Required, non-blank school year; at most 20 characters.", minLength = 1)
            @Size(max = 20) String schoolYear,
            @Size(max = 4, message = "An idea can have at most five owners including its creator.")
            @ArraySchema(arraySchema = @Schema(description = "For student-authored ideas, optional distinct IDs of other "
                    + "student collaborators; exclude the creator and provide at most four (five owners total). "
                    + "Teacher-authored ideas cannot have collaborators."),
                    maxItems = 4, uniqueItems = true, schema = @Schema(description = "Collaborator account ID.", format = "uuid"))
            List<@NotNull UUID> collaboratorIds) {
    }

    public record SpecificationRequest(
            @NotEmpty @ArraySchema(arraySchema = @Schema(description = "At least one objective."),
                    minItems = 1, schema = @Schema(description = "Non-blank objective; at most 2,000 characters.",
                            minLength = 1))
            List<@NotBlank @Size(max = 2000) String> objectives,
            @NotEmpty @ArraySchema(arraySchema = @Schema(description = "At least one intended user group."),
                    minItems = 1, schema = @Schema(description = "Non-blank intended user group; at most 1,000 characters.",
                            minLength = 1))
            List<@NotBlank @Size(max = 1000) String> intendedUsers,
            @NotEmpty @ArraySchema(arraySchema = @Schema(description = "At least one requirement."),
                    minItems = 1, schema = @Schema(description = "Non-blank requirement; at most 2,000 characters.",
                            minLength = 1))
            List<@NotBlank @Size(max = 2000) String> requirements,
            @NotEmpty @ArraySchema(arraySchema = @Schema(description = "At least one deliverable."),
                    minItems = 1, schema = @Schema(description = "Non-blank deliverable; at most 1,000 characters.",
                            minLength = 1))
            List<@NotBlank @Size(max = 1000) String> deliverables,
            @NotBlank @Size(max = 5000)
            @Schema(description = "Required, non-blank specification scope; at most 5,000 characters.", minLength = 1)
            String scope,
            @NotEmpty @ArraySchema(arraySchema = @Schema(description = "At least one dependency."),
                    minItems = 1, schema = @Schema(description = "Non-blank dependency; at most 1,000 characters.",
                            minLength = 1))
            List<@NotBlank @Size(max = 1000) String> dependencies) {
    }

    public record ReviewRequest(
            @NotBlank @Schema(description = "Required, non-blank decision. Formal applications accept APPROVE, "
                    + "REQUEST_REVISION, or DECLINE; specifications accept ACCEPT_FOR_APPLICATIONS, "
                    + "REQUEST_REVISION, or DECLINE.", minLength = 1)
            String decision,
            @NotBlank @Size(max = 5000)
            @Schema(description = "Required, non-blank written feedback; at most 5,000 characters.", minLength = 1)
            String feedback,
            @Min(1) @Max(5) @Schema(description = "Optional rating from 1 to 5.") Integer workSize,
            @Min(1) @Max(5) @Schema(description = "Optional rating from 1 to 5.") Integer curriculumRelevance,
            @Min(1) @Max(5) @Schema(description = "Optional rating from 1 to 5.") Integer technicalComplexity,
            @Min(1) @Max(5) @Schema(description = "Optional rating from 1 to 5.") Integer feasibility,
            @Min(1) @Max(5) @Schema(description = "Optional rating from 1 to 5.") Integer learningValue,
            @Min(1) @Max(5) @Schema(description = "Optional rating from 1 to 5.") Integer resourceNeeds,
            @Min(1) @Max(5) @Schema(description = "Optional rating from 1 to 5.") Integer riskAndDependencies,
            @Size(max = 2000) @Schema(description = "Optional recommendation; at most 2,000 characters.")
            String recommendation) {
    }

    /**
     * Idea review request: ratings may be omitted, but a rating that is present as an explicit JSON null is
     * rejected. Bean properties are used because Jackson only applies the null check to supplied properties.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IdeaReviewRequest {
        @NotBlank
        @Schema(description = "Required, non-blank idea-review decision: APPROVE_FOR_SPECIFICATION, "
                + "REQUEST_REVISION, or DECLINE.", minLength = 1)
        private String decision;
        @NotBlank
        @Size(max = 5000)
        @Schema(description = "Required, non-blank written feedback; at most 5,000 characters.", minLength = 1)
        private String feedback;
        @Min(1)
        @Max(5)
        @Schema(description = "Optional rating from 1 to 5.")
        private Integer workSize;
        @Min(1)
        @Max(5)
        @Schema(description = "Optional rating from 1 to 5.")
        private Integer curriculumRelevance;
        @Min(1)
        @Max(5)
        @Schema(description = "Optional rating from 1 to 5.")
        private Integer technicalComplexity;
        @Min(1)
        @Max(5)
        @Schema(description = "Optional rating from 1 to 5.")
        private Integer feasibility;
        @Min(1)
        @Max(5)
        @Schema(description = "Optional rating from 1 to 5.")
        private Integer learningValue;
        @Min(1)
        @Max(5)
        @Schema(description = "Optional rating from 1 to 5.")
        private Integer resourceNeeds;
        @Min(1)
        @Max(5)
        @Schema(description = "Optional rating from 1 to 5.")
        private Integer riskAndDependencies;
        @Size(max = 2000)
        @Schema(description = "Optional recommendation; at most 2,000 characters.")
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
                    "NOT_WILLING_TO_SUPERVISE", "NOT_AVAILABLE"},
                    description = "Required supervision preference: WANTS_TO_SUPERVISE, WILLING_TO_SUPERVISE, "
                            + "NOT_WILLING_TO_SUPERVISE, or NOT_AVAILABLE.", minLength = 1)
            String preference,
            @Size(max = 2000) @Schema(description = "Optional note; at most 2,000 characters.") String note) {
    }

    public record SupervisorRequest(
            @NotNull @Schema(description = "ID of the teacher to assign as supervisor.", format = "uuid")
            UUID teacherId,
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
            @NotBlank @Size(max = 20) @Schema(description = "Required, non-blank school year; at most 20 characters.",
                    minLength = 1)
            String schoolYear,
            @NotNull @Schema(description = "Period opening instant in ISO 8601 date-time format.", format = "date-time")
            Instant opensAt,
            @NotNull @Schema(description = "Period closing instant in ISO 8601 date-time format; must be after opensAt.",
                    format = "date-time")
            Instant closesAt,
            @NotEmpty @ArraySchema(arraySchema = @Schema(
                    description = "At least three distinct accepted project IDs for this period's school year must be included."),
                    minItems = 3, uniqueItems = true, schema = @Schema(description = "Project ID.", format = "uuid"))
            List<@NotNull UUID> projectIds) {
    }

    public record AllocationPinSnapshot(UUID studentId, UUID projectId, Instant savedAt) {
    }

    public record AllocationPreferenceSnapshot(UUID projectId, int rank) {
    }

    public record AllocationApplicationSnapshot(UUID applicationId, UUID studentId,
            List<AllocationPreferenceSnapshot> preferences) {
    }

    public record AllocationAssignmentResponse(
            @NotNull @Schema(description = "Assigned student ID.", requiredMode = Schema.RequiredMode.REQUIRED,
                    format = "uuid") UUID studentId,
            @NotNull @Schema(description = "Assigned project ID.", requiredMode = Schema.RequiredMode.REQUIRED,
                    format = "uuid") UUID projectId,
            @NotNull @Schema(description = "Rank of the assigned project in the student's preferences; zero when pinned.",
                    requiredMode = Schema.RequiredMode.REQUIRED) int preferenceRank,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "True when fixed to this project by an administrator pin.") Boolean pinned,
            String studentDisplayName, String projectTitle, String className, List<String> electiveSubjectNames) {
    }

    public record AllocationUnassignedStudentResponse(UUID studentId, String reason,
            List<UUID> rankedProjectIds, String explanation, String studentDisplayName,
            String className, List<String> electiveSubjectNames) {
    }

    public record AllocationUnassignedProjectResponse(UUID projectId, String reason,
            int interestedStudents, String projectTitle) {
    }

    public record AllocationResultResponse(List<AllocationAssignmentResponse> assignments,
            List<AllocationUnassignedStudentResponse> unassignedStudents,
            List<AllocationUnassignedProjectResponse> unassignedProjects,
            int assignedStudents, long preferenceScore) {
    }

    public record AllocationRunResponse(UUID id, String status, UUID periodId, Instant createdAt,
            Boolean initial, @Schema(description = "True when this run was started before the application deadline; otherwise false.",
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    defaultValue = "false")
            Boolean early, List<UUID> projectIds, List<AllocationApplicationSnapshot> applications,
            List<AllocationPinSnapshot> pins, AllocationResultResponse result, String error,
            Boolean inputSnapshotComplete, Instant finalizedAt) {
    }

    public record AllocationRunPageResponse(List<AllocationRunResponse> content, int page, int size,
            long totalElements, int totalPages) {
    }

    public record AllocationPinRequest(
            @NotNull @Schema(description = "Project to pin for this student.", requiredMode = Schema.RequiredMode.REQUIRED,
                    format = "uuid") UUID projectId,
            @Schema(description = "Explicitly acknowledge that the student did not rank the selected project.",
                    defaultValue = "false") Boolean acknowledgeUnranked) {
    }

    public record AllocationPinResponse(
            @NotNull @Schema(description = "Pinned student ID.", requiredMode = Schema.RequiredMode.REQUIRED,
                    format = "uuid") UUID studentId,
            @NotNull @Schema(description = "Current student display name.", requiredMode = Schema.RequiredMode.REQUIRED)
            String studentDisplayName,
            @NotNull @Schema(description = "Pinned project ID.", requiredMode = Schema.RequiredMode.REQUIRED,
                    format = "uuid") UUID projectId,
            @NotNull @Schema(description = "Current project title.", requiredMode = Schema.RequiredMode.REQUIRED)
            String projectTitle,
            @NotNull @Schema(description = "Whether the project is among the student's ranked choices.",
                    requiredMode = Schema.RequiredMode.REQUIRED) Boolean rankedChoice,
            @NotNull @Schema(description = "When the pin was saved, as an ISO 8601 instant.",
                    requiredMode = Schema.RequiredMode.REQUIRED, format = "date-time") Instant savedAt) {
    }

    public record StudentApplicationRequest(
            @NotEmpty @ArraySchema(arraySchema = @Schema(description =
                    "At least three distinct project IDs from the period catalogue, ranked in array order.",
                    requiredMode = Schema.RequiredMode.REQUIRED),
                    minItems = 3, uniqueItems = true, schema = @Schema(description = "Project ID.", format = "uuid"))
            List<@NotNull UUID> projectIds) {
    }

    public record IndividualTopic(
            @NotNull @Schema(description = "Allocated group member's account ID.", format = "uuid") UUID studentId,
            @NotBlank @Size(max = 200)
            @Schema(description = "Non-blank individual topic; at most 200 characters.", minLength = 1)
            String topic) {
    }

    public record StudentText(
            @NotNull @Schema(description = "Allocated group member's account ID.", format = "uuid") UUID studentId,
            @NotBlank @Size(max = 2400)
            @Schema(description = "Non-blank text; at most 2,400 characters per member.", minLength = 1)
            String text) {
    }

    public record Milestone(
            @NotBlank @Size(max = 1000)
            @Schema(description = "Non-blank milestone description; at most 1,000 characters.", minLength = 1)
            String text,
            @NotNull @Schema(description = "Milestone date in ISO 8601 calendar-date format.", format = "date")
            LocalDate date) {
    }

    public record FormalApplicationRequest(
            @NotBlank @Size(max = 200)
            @Schema(description = "Required, non-blank project topic; at most 200 characters.", minLength = 1)
            String topic,
            @NotEmpty @ArraySchema(arraySchema = @Schema(description =
                    "Exactly one individual topic for each allocated group member; the group contains 3 to 5 students."),
                    minItems = 3, maxItems = 5)
            List<@Valid IndividualTopic> individualTopics,
            @NotBlank @Size(max = 400)
            @Schema(description = "Required, non-blank initial situation; at most 400 characters.", minLength = 1)
            String initialSituation,
            @NotEmpty @ArraySchema(arraySchema = @Schema(description =
                    "Exactly one entry per allocated group member (3 to 5 entries); text values combined must be at most 2,400 characters."),
                    minItems = 3, maxItems = 5)
            List<@Valid StudentText> investigationConcerns,
            @NotBlank @Size(max = 400)
            @Schema(description = "Required, non-blank objective; at most 400 characters.", minLength = 1)
            String objective,
            @NotEmpty @ArraySchema(arraySchema = @Schema(description =
                    "Exactly one entry per allocated group member (3 to 5 entries); text values combined must be at most 2,400 characters."),
                    minItems = 3, maxItems = 5)
            List<@Valid StudentText> plannedResults,
            @NotEmpty @ArraySchema(arraySchema = @Schema(description = "At least one milestone."), minItems = 1)
            List<@Valid Milestone> milestones,
            @Size(max = 1000) @Schema(description = "Optional cooperation partner; at most 1,000 characters.")
            String cooperationPartner) {
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

    public record IdeaOwnerRequest(
            @NotNull @Schema(description = "ID of the student to add as a co-owner.", format = "uuid")
            UUID studentId) {
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
