package com.flightlog.web;

import com.flightlog.security.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
            Role role) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record TokenResponse(String token, String tokenType, long expiresIn, UserResponse user) {
    }

    public record UserResponse(UUID id, String username, Role role) {
    }

    public record RoleRequest(@NotNull Role role) {
    }

    public record IdeaRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 5000) String description,
            @NotBlank @Size(max = 2000) String intendedOutcome,
            @NotEmpty List<@NotBlank @Size(max = 1000) String> scopePoints,
            @Size(max = 50) List<@NotBlank @Size(max = 200) String> learningTopics,
            @NotBlank @Size(max = 20) String schoolYear) {
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

    public record SupervisionPreferenceRequest(
            @NotBlank String preference, @Size(max = 2000) String note) {
    }

    public record SupervisorRequest(@NotNull UUID teacherId) {
    }

    public record ApplicationPeriodRequest(
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

    public record SupervisionPreferenceResponse(UUID id, UUID teacherId, String preference, String note) {
    }

    public record SupervisorResponse(UUID id, UUID teacherId, String status) {
    }

    public record IdeaResponse(
            UUID id,
            UUID ownerId,
            String status,
            String title,
            String description,
            String intendedOutcome,
            List<String> scopePoints,
            List<String> learningTopics,
            String schoolYear,
            SpecificationResponse specification,
            List<ReviewResponse> reviews,
            List<SupervisionPreferenceResponse> supervisionPreferences) {
    }

    public record ApplicationPeriodResponse(
            UUID id,
            String schoolYear,
            Instant opensAt,
            Instant closesAt,
            String status,
            List<UUID> projectIds) {
    }

    public record PreferenceResponse(UUID projectId, int rank) {
    }

    public record StudentApplicationResponse(
            UUID id,
            UUID studentId,
            UUID periodId,
            String status,
            List<PreferenceResponse> preferences) {
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
