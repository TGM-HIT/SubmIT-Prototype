package com.flightlog.idea;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.user.UserAccount;
import com.flightlog.user.UserAccountRepository;
import com.flightlog.web.ApiDtos.IdeaResponse;
import com.flightlog.web.ApiDtos.ReviewResponse;
import com.flightlog.web.ApiDtos.SpecificationResponse;
import com.flightlog.web.ApiDtos.SupervisionPreferenceResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class IdeaResponseMapper {
    private static final String SPECIFICATION = "specification";
    private static final String REVIEW = "review";
    private static final String SUPERVISION = "supervision_preference";

    private final RecordStore store;
    private final UserAccountRepository users;

    IdeaResponseMapper(RecordStore store, UserAccountRepository users) {
        this.store = store;
        this.users = users;
    }

    IdeaResponse response(StoredRecord record, boolean includeHistory) {
        List<StoredRecord> reviews = includeHistory ? children(REVIEW, record) : List.of();
        List<StoredRecord> preferences = includeHistory ? children(SUPERVISION, record) : List.of();
        Set<UUID> userIds = new java.util.LinkedHashSet<>();
        userIds.add(UUID.fromString(record.getOwnerId()));
        reviews.stream().map(child -> UUID.fromString(child.getOwnerId())).forEach(userIds::add);
        preferences.stream().map(child -> UUID.fromString(child.getOwnerId())).forEach(userIds::add);
        Map<UUID, String> names = displayNames(userIds);
        return response(record, includeHistory, reviews, preferences, names);
    }

    private IdeaResponse response(StoredRecord record, boolean includeHistory, List<StoredRecord> reviewRecords,
            List<StoredRecord> preferenceRecords, Map<UUID, String> names) {
        ObjectNode payload = store.parse(record);
        SpecificationResponse specification = null;
        List<ReviewResponse> reviews = List.of();
        List<SupervisionPreferenceResponse> preferences = List.of();
        if (includeHistory) {
            StoredRecord currentSpecification = store.repository()
                    .findFirstByTypeAndParentIdOrderByCreatedAtDesc(SPECIFICATION, record.getId().toString())
                    .orElse(null);
            if (currentSpecification != null) {
                specification = specificationResponse(currentSpecification);
            }
            reviews = reviewRecords.stream().map(review -> reviewResponse(review, names)).toList();
            preferences = preferenceRecords.stream().map(preference -> supervisionResponse(preference, names)).toList();
        }
        UUID ownerId = UUID.fromString(record.getOwnerId());
        return new IdeaResponse(record.getId(), ownerId, names.get(ownerId), record.getStatus(),
                payload.path("title").asText(), payload.path("description").asText(),
                payload.path("intendedOutcome").asText(), strings(payload, "scopePoints"),
                strings(payload, "learningTopics"), payload.path("schoolYear").asText(),
                specification, reviews, preferences);
    }

    List<IdeaResponse> responses(List<StoredRecord> records) {
        Set<UUID> ownerIds = records.stream().map(record -> UUID.fromString(record.getOwnerId()))
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<UUID, String> names = displayNames(ownerIds);
        return records.stream()
                .map(record -> response(record, false, List.of(), List.of(), names))
                .toList();
    }

    SpecificationResponse specificationResponse(StoredRecord record) {
        ObjectNode payload = store.parse(record);
        return new SpecificationResponse(record.getId(), record.getStatus(),
                strings(payload, "objectives"), strings(payload, "intendedUsers"),
                strings(payload, "requirements"), strings(payload, "deliverables"),
                payload.path("scope").asText(), strings(payload, "dependencies"),
                payload.path("revisionId").asText(null));
    }

    ReviewResponse reviewResponse(StoredRecord record) {
        return reviewResponse(record, displayNames(Set.of(UUID.fromString(record.getOwnerId()))));
    }

    private ReviewResponse reviewResponse(StoredRecord record, Map<UUID, String> names) {
        ObjectNode payload = store.parse(record);
        UUID teacherId = UUID.fromString(record.getOwnerId());
        return new ReviewResponse(record.getId(), teacherId, names.get(teacherId),
                reviewType(record, payload), record.getStatus(), payload.path("feedback").asText(),
                integer(payload, "workSize"),
                integer(payload, "curriculumRelevance"), integer(payload, "technicalComplexity"),
                integer(payload, "feasibility"), integer(payload, "learningValue"),
                integer(payload, "resourceNeeds"), integer(payload, "riskAndDependencies"),
                payload.path("recommendation").asText(null));
    }

    private String reviewType(StoredRecord record, ObjectNode payload) {
        String reviewType = payload.path("reviewType").asText(null);
        if (reviewType != null) {
            return reviewType;
        }
        return switch (record.getStatus()) {
            case "APPROVE_FOR_SPECIFICATION" -> "IDEA";
            case "ACCEPT_FOR_APPLICATIONS" -> "SPECIFICATION";
            default -> "UNKNOWN";
        };
    }

    SupervisionPreferenceResponse supervisionResponse(StoredRecord record) {
        return supervisionResponse(record, displayNames(Set.of(UUID.fromString(record.getOwnerId()))));
    }

    private SupervisionPreferenceResponse supervisionResponse(StoredRecord record, Map<UUID, String> names) {
        ObjectNode payload = store.parse(record);
        UUID teacherId = UUID.fromString(record.getOwnerId());
        return new SupervisionPreferenceResponse(record.getId(), teacherId, names.get(teacherId),
                record.getStatus(), payload.path("note").asText(null));
    }

    private Map<UUID, String> displayNames(Set<UUID> userIds) {
        return users.findAllById(userIds).stream()
                .collect(Collectors.toMap(UserAccount::getId, UserAccount::getDisplayName));
    }

    private List<StoredRecord> children(String type, StoredRecord parent) {
        return store.page(type, null, parent.getId().toString(), PageRequest.of(0, 100)).getContent();
    }

    private Integer integer(ObjectNode payload, String name) {
        return payload.hasNonNull(name) ? payload.path(name).asInt() : null;
    }

    private List<String> strings(ObjectNode payload, String field) {
        List<String> values = new ArrayList<>();
        payload.path(field).forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }
}
