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
            @Schema(description = "Required login name; whitespace-only values are rejected; 3 to 80 characters.")
            String username,
            @NotBlank @Size(min = 12, max = 128)
            @Schema(description = "Required password; whitespace-only values are rejected; 12 to 128 characters.")
            String password,
            @NotNull @Schema(description = "Required account name; leading and trailing whitespace is trimmed, "
                    + "a value blank after trimming is rejected, and the trimmed name is limited to 120 characters.")
            String displayName,
            Role role) {
    }

    public record LoginRequest(
            @NotBlank @Schema(description = "Required login name; at least one non-whitespace character.",
                    minLength = 1) String username,
            @NotBlank @Schema(description = "Required password; at least one non-whitespace character.",
                    minLength = 1) String password) {
    }

    @Schema(requiredProperties = {"token", "tokenType", "expiresIn", "user"})
    public record TokenResponse(
            @Schema(description = "Signed authentication token.") String token,
            @Schema(description = "Authentication scheme for the token.") String tokenType,
            @Schema(description = "Token lifetime in seconds.") long expiresIn,
            @Schema(description = "Authenticated account response.") UserResponse user) {
    }

    @Schema(requiredProperties = {"id", "username", "displayName", "role"})
    public record UserResponse(
            @Schema(description = "Account ID.") UUID id,
            @Schema(description = "Login name.") String username,
            @Schema(description = "Current account display name.", requiredMode = Schema.RequiredMode.REQUIRED)
            String displayName,
            @Schema(description = "Assigned account role.") Role role,
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

    @Schema(requiredProperties = {"id", "name"})
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

    @Schema(requiredProperties = {"studentId", "displayName", "classId", "className", "electiveSubjectIds",
            "electiveSubjectNames"})
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

    @Schema(requiredProperties = {"status", "error", "message"})
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

    @Schema(requiredProperties = {"studentId", "projectId", "savedAt"})
    public record AllocationPinSnapshot(
            @Schema(description = "Pinned student account ID.") UUID studentId,
            @Schema(description = "Pinned project ID.") UUID projectId,
            @Schema(description = "Instant when the pin was saved.", format = "date-time") Instant savedAt) {
    }

    @Schema(requiredProperties = {"projectId", "rank"})
    public record AllocationPreferenceSnapshot(
            @Schema(description = "Preferred project ID.") UUID projectId,
            @Schema(description = "Preference rank, starting at one.") int rank) {
    }

    @Schema(requiredProperties = {"applicationId", "studentId", "preferences"})
    public record AllocationApplicationSnapshot(
            @Schema(description = "Application record ID.") UUID applicationId,
            @Schema(description = "Applicant account ID.") UUID studentId,
            @Schema(description = "Snapshot of ranked project preferences.") List<AllocationPreferenceSnapshot> preferences) {
    }

    @Schema(requiredProperties = {"studentId", "projectId", "preferenceRank", "pinned", "studentDisplayName",
            "projectTitle"})
    public record AllocationAssignmentResponse(
            @NotNull @Schema(description = "Assigned student ID.", requiredMode = Schema.RequiredMode.REQUIRED,
                    format = "uuid") UUID studentId,
            @NotNull @Schema(description = "Assigned project ID.", requiredMode = Schema.RequiredMode.REQUIRED,
                    format = "uuid") UUID projectId,
            @NotNull @Schema(description = "Rank of the assigned project in the student's preferences; zero when pinned.",
                    requiredMode = Schema.RequiredMode.REQUIRED) int preferenceRank,
            @NotNull @Schema(requiredMode = Schema.RequiredMode.REQUIRED,
                    description = "True when fixed to this project by an administrator pin.") Boolean pinned,
            @Schema(description = "Current student display name.") String studentDisplayName,
            @Schema(description = "Current project title.") String projectTitle,
            String className, List<String> electiveSubjectNames) {
    }

    @Schema(requiredProperties = {"studentId", "reason", "rankedProjectIds", "studentDisplayName"})
    public record AllocationUnassignedStudentResponse(
            @Schema(description = "Unassigned student account ID.") UUID studentId,
            @Schema(description = "Reason no project assignment was made.") String reason,
            @Schema(description = "Project IDs ranked by the student.") List<UUID> rankedProjectIds,
            String explanation,
            @Schema(description = "Current student display name.") String studentDisplayName,
            String className, List<String> electiveSubjectNames) {
    }

    @Schema(requiredProperties = {"projectId", "reason", "interestedStudents", "projectTitle"})
    public record AllocationUnassignedProjectResponse(
            @Schema(description = "Project ID without an assigned group.") UUID projectId,
            @Schema(description = "Reason no group was assigned.") String reason,
            @Schema(description = "Number of interested students.") int interestedStudents,
            @Schema(description = "Current project title.") String projectTitle) {
    }

    @Schema(requiredProperties = {"assignments", "unassignedStudents", "unassignedProjects", "assignedStudents",
            "preferenceScore"})
    public record AllocationResultResponse(
            @Schema(description = "Project assignments produced by this run.") List<AllocationAssignmentResponse> assignments,
            @Schema(description = "Students without an assignment.") List<AllocationUnassignedStudentResponse> unassignedStudents,
            @Schema(description = "Projects without an assigned group.") List<AllocationUnassignedProjectResponse> unassignedProjects,
            @Schema(description = "Number of students assigned to projects.") int assignedStudents,
            @Schema(description = "Total preference score for the allocation.") long preferenceScore) {
    }

    @Schema(requiredProperties = {"id", "status", "periodId", "createdAt", "initial", "early", "projectIds",
            "applications", "pins"})
    public record AllocationRunResponse(
            @Schema(description = "Allocation-run ID.") UUID id,
            @Schema(description = "Current allocation-run status.") String status,
            @Schema(description = "Application-period ID.") UUID periodId,
            @Schema(description = "Instant when the run was created.", format = "date-time") Instant createdAt,
            @Schema(description = "Whether this is the initial allocation run.") Boolean initial,
            @Schema(description = "True when this run was started before the application deadline; otherwise false.",
                    requiredMode = Schema.RequiredMode.REQUIRED,
                    defaultValue = "false")
            Boolean early,
            @Schema(description = "Project IDs included in the run snapshot.") List<UUID> projectIds,
            @Schema(description = "Application snapshots used by the run.") List<AllocationApplicationSnapshot> applications,
            @Schema(description = "Administrator pin snapshots used by the run.") List<AllocationPinSnapshot> pins,
            AllocationResultResponse result, String error,
            Boolean inputSnapshotComplete, Instant finalizedAt) {
    }

    @Schema(requiredProperties = {"content", "page", "size", "totalElements", "totalPages"})
    public record AllocationRunPageResponse(
            @Schema(description = "Allocation runs returned in this page.") List<AllocationRunResponse> content,
            @Schema(description = "Zero-based index of this page.") int page,
            @Schema(description = "Maximum number of items requested per page.") int size,
            @Schema(description = "Total number of matching allocation runs.") long totalElements,
            @Schema(description = "Number of pages containing all matching runs.") int totalPages) {
    }

    public record AllocationPinRequest(
            @NotNull @Schema(description = "Project to pin for this student.", requiredMode = Schema.RequiredMode.REQUIRED,
                    format = "uuid") UUID projectId,
            @Schema(description = "Explicitly acknowledge that the student did not rank the selected project.",
                    defaultValue = "false") Boolean acknowledgeUnranked) {
    }

    @Schema(requiredProperties = {"studentId", "studentDisplayName", "projectId", "projectTitle", "rankedChoice",
            "savedAt"})
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

    @Schema(requiredProperties = {"content", "page", "size", "totalElements", "totalPages"})
    public record PageResponse<T>(
            @Schema(description = "Items returned in this page.") List<T> content,
            @Schema(description = "Zero-based index of this page.") int page,
            @Schema(description = "Maximum number of items requested per page.") int size,
            @Schema(description = "Total number of matching items across all pages.") long totalElements,
            @Schema(description = "Number of pages containing all matching items.") int totalPages) {
    }

    @Schema(requiredProperties = {"id", "status", "objectives", "intendedUsers", "requirements", "deliverables",
            "scope", "dependencies", "revisionId"})
    public record SpecificationResponse(
            @Schema(description = "Specification record ID.") UUID id,
            @Schema(description = "Current specification workflow status.") String status,
            @Schema(description = "Project objectives.") List<String> objectives,
            @Schema(description = "Intended users of the project.") List<String> intendedUsers,
            @Schema(description = "Project requirements.") List<String> requirements,
            @Schema(description = "Project deliverables.") List<String> deliverables,
            @Schema(description = "Project scope.") String scope,
            @Schema(description = "External or internal project dependencies.") List<String> dependencies,
            @Schema(description = "Identifier of the source revision, or null when unavailable.",
                    types = {"string", "null"}) String revisionId) {
    }

    @Schema(requiredProperties = {"id", "teacherId", "teacherDisplayName", "reviewType", "decision", "feedback",
            "workSize", "curriculumRelevance", "technicalComplexity", "feasibility", "learningValue",
            "resourceNeeds", "riskAndDependencies", "recommendation"})
    public record ReviewResponse(
            @Schema(description = "Review record ID.") UUID id,
            @Schema(description = "Review author's account ID.") UUID teacherId,
            @Schema(description = "Current account display name; included only in authorized idea history.",
                    types = {"string", "null"})
            String teacherDisplayName,
            @Schema(description = "Kind of review.") String reviewType,
            @Schema(description = "Recorded review decision.") String decision,
            @Schema(description = "Written reviewer feedback.") String feedback,
            @Schema(description = "Optional work-size rating; null when not recorded.",
                    types = {"integer", "null"}) Integer workSize,
            @Schema(description = "Optional curriculum-relevance rating; null when not recorded.",
                    types = {"integer", "null"}) Integer curriculumRelevance,
            @Schema(description = "Optional technical-complexity rating; null when not recorded.",
                    types = {"integer", "null"}) Integer technicalComplexity,
            @Schema(description = "Optional feasibility rating; null when not recorded.",
                    types = {"integer", "null"}) Integer feasibility,
            @Schema(description = "Optional learning-value rating; null when not recorded.",
                    types = {"integer", "null"}) Integer learningValue,
            @Schema(description = "Optional resource-needs rating; null when not recorded.",
                    types = {"integer", "null"}) Integer resourceNeeds,
            @Schema(description = "Optional risk-and-dependencies rating; null when not recorded.",
                    types = {"integer", "null"}) Integer riskAndDependencies,
            @Schema(description = "Optional recommendation; null when not recorded.",
                    types = {"string", "null"}) String recommendation) {
    }

    @Schema(requiredProperties = {"id", "teacherId", "teacherDisplayName", "preference", "note"})
    public record SupervisionPreferenceResponse(
            @Schema(description = "Preference record ID.") UUID id,
            @Schema(description = "Teacher account ID.") UUID teacherId,
            @Schema(description = "Current account display name; included only in authorized idea history.",
                    types = {"string", "null"})
            String teacherDisplayName,
            @Schema(description = "Recorded supervision preference.") String preference,
            @Schema(description = "Optional note; null when not recorded.", types = {"string", "null"}) String note) {
    }

    @Schema(requiredProperties = {"id", "teacherId", "status"})
    public record SupervisorResponse(
            @Schema(description = "Supervisor assignment record ID.") UUID id,
            @Schema(description = "Assigned teacher account ID.") UUID teacherId,
            @Schema(description = "Supervisor assignment status.") String status) {
    }

    @Schema(requiredProperties = {"id", "displayName", "creator"})
    public record IdeaOwnerResponse(
            @Schema(description = "Owner account ID.") UUID id,
            @Schema(description = "Current owner display name, or null when the account is unavailable.",
                    types = {"string", "null"}) String displayName,
            @Schema(description = "Whether this owner created the idea.") boolean creator,
            @Schema(description = "Current class name; only included in authenticated teacher and administrator responses.")
            @JsonInclude(JsonInclude.Include.NON_NULL) String className,
            @Schema(description = "Current elective subject names; only included in authenticated teacher and administrator responses.")
            @JsonInclude(JsonInclude.Include.NON_NULL) List<String> electiveSubjectNames) {
    }

    @Schema(requiredProperties = {"id", "displayName"})
    public record StudentLookupResponse(
            @Schema(description = "Student account ID.") UUID id,
            @Schema(description = "Current student display name.") String displayName) {
    }

    public record IdeaOwnerRequest(
            @NotNull @Schema(description = "ID of the student to add as a co-owner.", format = "uuid")
            UUID studentId) {
    }

    @Schema(requiredProperties = {"id", "ownerId", "creatorId", "owners", "ownerDisplayName", "status", "title",
            "description", "intendedOutcome", "scopePoints", "learningTopics", "schoolYear", "specification",
            "reviews", "supervisionPreferences"})
    public record IdeaResponse(
            @Schema(description = "Idea record ID.") UUID id,
            @Schema(description = "Primary owner's account ID.") UUID ownerId,
            @Schema(description = "Creator's account ID.") UUID creatorId,
            @Schema(description = "Owners of the idea, including its creator.") List<IdeaOwnerResponse> owners,
            @Schema(description = "Current account display name; included only in authorized idea responses.",
                    types = {"string", "null"})
            String ownerDisplayName,
            @Schema(description = "Idea workflow status.") String status,
            @Schema(description = "Idea title.") String title,
            @Schema(description = "Idea description.") String description,
            @Schema(description = "Intended outcome of the idea.") String intendedOutcome,
            @Schema(description = "Scope points supplied for the idea.") List<String> scopePoints,
            @Schema(description = "Learning topics supplied for the idea.") List<String> learningTopics,
            @Schema(description = "School year associated with the idea.") String schoolYear,
            @JsonInclude(JsonInclude.Include.NON_NULL) String confirmedSupervisorDisplayName,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Whether the current author account is a teacher; included only in staff responses.")
            Boolean teacherIdea,
            @Schema(description = "Current specification, or null if none exists.",
                    types = {"object", "null"}, schemaResolution = Schema.SchemaResolution.INLINE)
            SpecificationResponse specification,
            @Schema(description = "Idea review history available to the caller.") List<ReviewResponse> reviews,
            @Schema(description = "Supervision preference history available to the caller.")
            List<SupervisionPreferenceResponse> supervisionPreferences) {
    }

    @Schema(requiredProperties = {"id", "label", "schoolYear", "opensAt", "closesAt", "status", "projectIds"})
    public record ApplicationPeriodResponse(
            @Schema(description = "Application-period record ID.") UUID id,
            @NotNull
            @Schema(maxLength = 100,
                    description = "Period label; legacy records without a label are presented using their "
                            + "school year. At most 100 characters.")
            String label,
            @Schema(description = "School year served by the application period.") String schoolYear,
            @Schema(description = "Instant when applications open.", format = "date-time") Instant opensAt,
            @Schema(description = "Instant when applications close.", format = "date-time") Instant closesAt,
            @Schema(description = "Current application-period status.") String status,
            @Schema(description = "Project IDs available in the period catalogue.") List<UUID> projectIds,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<ProjectTitleResponse> projectTitles,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Present only to the authenticated student; status of that student's application, or NOT_SUBMITTED.")
            String studentApplicationStatus,
            @JsonInclude(JsonInclude.Include.NON_NULL)
            @Schema(description = "Present only for the authenticated student's own submitted application.")
            StudentAssignmentResponse studentAssignment) {
    }

    @Schema(requiredProperties = {"objectives", "intendedUsers", "requirements", "deliverables", "scope",
            "dependencies"})
    public record CatalogueSpecificationResponse(
            @Schema(description = "Project objectives.") List<String> objectives,
            @Schema(description = "Intended users of the project.") List<String> intendedUsers,
            @Schema(description = "Project requirements.") List<String> requirements,
            @Schema(description = "Project deliverables.") List<String> deliverables,
            @Schema(description = "Project scope.") String scope,
            @Schema(description = "Project dependencies.") List<String> dependencies) {
    }

    @Schema(requiredProperties = {"id", "title", "description", "intendedOutcome", "scopePoints", "learningTopics",
            "schoolYear", "specification"})
    public record CatalogueProjectResponse(
            @Schema(description = "Project idea ID.") UUID id,
            @Schema(description = "Project title.") String title,
            @Schema(description = "Project description.") String description,
            @Schema(description = "Intended project outcome.") String intendedOutcome,
            @Schema(description = "Project scope points.") List<String> scopePoints,
            @Schema(description = "Project learning topics.") List<String> learningTopics,
            @Schema(description = "Associated school year.") String schoolYear,
            @Schema(description = "Project specification, or null if it has not been recorded.",
                    types = {"object", "null"}, schemaResolution = Schema.SchemaResolution.INLINE)
            CatalogueSpecificationResponse specification) {
    }

    @Schema(requiredProperties = {"projectId", "title"})
    public record ProjectTitleResponse(
            @Schema(description = "Project idea ID.") UUID projectId,
            @Schema(description = "Current project title.") String title) {
    }

    @Schema(requiredProperties = {"projectId", "rank", "projectTitle"})
    public record PreferenceResponse(
            @Schema(description = "Preferred project ID.") UUID projectId,
            @Schema(description = "Preference rank, starting at one.") int rank,
            @Schema(description = "Current title of the preferred project.") String projectTitle) {
    }

    @Schema(requiredProperties = {"state", "projectTitle", "preferenceRank", "groupMembers"})
    public record StudentAssignmentResponse(
            @Schema(description = "NO_RESULT, PRELIMINARY, FINAL, NOT_ASSIGNED_PRELIMINARY, or NOT_ASSIGNED_FINAL.",
                    allowableValues = {"NO_RESULT", "PRELIMINARY", "FINAL", "NOT_ASSIGNED_PRELIMINARY",
                            "NOT_ASSIGNED_FINAL"})
            String state,
            @Schema(description = "Current project title; present only when assigned.",
                    types = {"string", "null"})
            String projectTitle,
            @Schema(description = "The caller's rank for the assigned project, when present in their application.",
                    types = {"integer", "null"}) Integer preferenceRank,
            @Schema(description = "Current display names of this assigned project's group only; contains no IDs.")
            List<StudentGroupMemberResponse> groupMembers) {
    }

    @Schema(requiredProperties = {"displayName", "self"})
    public record StudentGroupMemberResponse(
            @Schema(description = "Current account display name, or Unknown student.") String displayName,
            @Schema(description = "True only for the authenticated caller.") boolean self) {
    }

    @Schema(requiredProperties = {"id", "studentId", "studentDisplayName", "periodId", "status", "preferences"})
    public record StudentApplicationResponse(
            @Schema(description = "Student application record ID.") UUID id,
            @Schema(description = "Applicant account ID.") UUID studentId,
            @Schema(description = "Current applicant display name.") String studentDisplayName,
            @Schema(description = "Application-period ID.") UUID periodId,
            @Schema(description = "Current application status.") String status,
            @Schema(description = "Ranked project preferences.") List<PreferenceResponse> preferences,
            @Schema(description = "Current class name; only included in authenticated teacher and administrator responses.")
            @JsonInclude(JsonInclude.Include.NON_NULL) String className,
            @Schema(description = "Current elective subject names; only included in authenticated teacher and administrator responses.")
            @JsonInclude(JsonInclude.Include.NON_NULL) List<String> electiveSubjectNames) {
    }

    @Schema(requiredProperties = {"id", "teacherId", "decision", "feedback"})
    public record FormalReviewResponse(
            @Schema(description = "Formal-review record ID.") UUID id,
            @Schema(description = "Reviewing teacher account ID.") UUID teacherId,
            @Schema(description = "Recorded review decision.") String decision,
            @Schema(description = "Written review feedback.") String feedback) {
    }

    @Schema(requiredProperties = {"id", "projectId", "status", "topic", "individualTopics", "initialSituation",
            "investigationConcerns", "objective", "plannedResults", "milestones", "cooperationPartner",
            "reviewHistory"})
    public record FormalApplicationResponse(
            @Schema(description = "Formal-application record ID.") UUID id,
            @Schema(description = "Associated project ID.") UUID projectId,
            @Schema(description = "Current formal-application status.") String status,
            @Schema(description = "Project topic stated in the application.") String topic,
            @Schema(description = "Individual topics recorded for group members.") List<IndividualTopic> individualTopics,
            @Schema(description = "Initial situation described by the group.") String initialSituation,
            @Schema(description = "Investigation concerns recorded for group members.")
            List<StudentText> investigationConcerns,
            @Schema(description = "Objective stated in the application.") String objective,
            @Schema(description = "Planned results recorded for group members.") List<StudentText> plannedResults,
            @Schema(description = "Application milestones.") List<Milestone> milestones,
            @Schema(description = "Optional cooperation partner; null when not supplied.",
                    types = {"string", "null"}) String cooperationPartner,
            @Schema(description = "Paginated formal-review history.") PageResponse<FormalReviewResponse> reviewHistory) {
    }
}
