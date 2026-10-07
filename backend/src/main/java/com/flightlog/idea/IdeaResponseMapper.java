package com.flightlog.idea;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.web.ApiDtos.IdeaResponse;
import com.flightlog.web.ApiDtos.ReviewResponse;
import com.flightlog.web.ApiDtos.SpecificationResponse;
import com.flightlog.web.ApiDtos.SupervisionPreferenceResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
class IdeaResponseMapper {
    private static final String SPECIFICATION = "specification";
    private static final String REVIEW = "review";
    private static final String SUPERVISION = "supervision_preference";

    private final RecordStore store;

    IdeaResponseMapper(RecordStore store) {
        this.store = store;
    }

    IdeaResponse response(StoredRecord record, boolean includeHistory) {
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
            reviews = children(REVIEW, record).stream().map(this::reviewResponse).toList();
            preferences = children(SUPERVISION, record).stream()
                    .map(this::supervisionResponse).toList();
        }
        return new IdeaResponse(record.getId(), UUID.fromString(record.getOwnerId()), record.getStatus(),
                payload.path("title").asText(), payload.path("description").asText(),
                payload.path("intendedOutcome").asText(), strings(payload, "scopePoints"),
                strings(payload, "learningTopics"), payload.path("schoolYear").asText(),
                specification, reviews, preferences);
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
        ObjectNode payload = store.parse(record);
        return new ReviewResponse(record.getId(), UUID.fromString(record.getOwnerId()),
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
        ObjectNode payload = store.parse(record);
        return new SupervisionPreferenceResponse(record.getId(), UUID.fromString(record.getOwnerId()),
                record.getStatus(), payload.path("note").asText(null));
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
