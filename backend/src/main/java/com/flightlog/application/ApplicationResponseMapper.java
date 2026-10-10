package com.flightlog.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.user.UserAccount;
import com.flightlog.user.UserAccountRepository;
import com.flightlog.web.ApiDtos.ApplicationPeriodResponse;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiDtos.PreferenceResponse;
import com.flightlog.web.ApiDtos.StudentApplicationResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
class ApplicationResponseMapper {
    private static final String PROJECT_IDS = "projectIds";
    private static final String OPENS_AT = "opensAt";
    private static final String CLOSES_AT = "closesAt";

    private final RecordStore store;
    private final UserAccountRepository users;

    ApplicationResponseMapper(RecordStore store, UserAccountRepository users) {
        this.store = store;
        this.users = users;
    }

    ApplicationPeriodResponse period(StoredRecord record) {
        ObjectNode data = store.parse(record);
        List<UUID> projects = new ArrayList<>();
        data.path(PROJECT_IDS).forEach(project -> projects.add(UUID.fromString(project.asText())));
        return new ApplicationPeriodResponse(record.getId(), data.path("schoolYear").asText(),
                Instant.parse(data.path(OPENS_AT).asText()), Instant.parse(data.path(CLOSES_AT).asText()),
                record.getStatus(), List.copyOf(projects));
    }

    StudentApplicationResponse application(StoredRecord record) {
        return application(record, displayName(record.getOwnerId()), Map.of());
    }

    private StudentApplicationResponse application(StoredRecord record, String displayName,
            Map<UUID, String> projectTitles) {
        ObjectNode payload = store.parse(record);
        List<PreferenceResponse> preferences = new ArrayList<>();
        payload.path("preferences").forEach(preference -> {
            UUID projectId = UUID.fromString(preference.path("projectId").asText());
            String title = projectTitles.get(projectId);
            if (title == null || title.isBlank()) {
                title = projectTitle(projectId);
            }
            preferences.add(new PreferenceResponse(projectId, preference.path("rank").asInt(),
                    title.isBlank() ? "Unknown project" : title));
        });
        return new StudentApplicationResponse(record.getId(), UUID.fromString(record.getOwnerId()), displayName,
                UUID.fromString(record.getParentId()), record.getStatus(), List.copyOf(preferences));
    }

    PageResponse<ApplicationPeriodResponse> periods(Page<StoredRecord> records) {
        return new PageResponse<>(records.map(this::period).getContent(), records.getNumber(),
                records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    PageResponse<StudentApplicationResponse> applications(Page<StoredRecord> records) {
        Map<UUID, String> names = new HashMap<>();
        records.getContent().stream().map(StoredRecord::getOwnerId).map(UUID::fromString).distinct()
                .forEach(id -> users.findById(id).ifPresent(user -> names.put(id, user.getDisplayName())));
        List<UUID> projectIds = records.getContent().stream()
                .flatMap(record -> store.parse(record).path("preferences").findValuesAsText("projectId").stream())
                .map(UUID::fromString).distinct().toList();
        Map<UUID, String> titles = new HashMap<>();
        store.repository().findAllById(projectIds).stream().filter(record -> "idea".equals(record.getType()))
                .forEach(record -> titles.put(record.getId(), store.parse(record).path("title").asText()));
        List<StudentApplicationResponse> content = records.getContent().stream()
                .map(record -> application(record,
                        names.getOrDefault(UUID.fromString(record.getOwnerId()), "Unknown student"), titles))
                .toList();
        return new PageResponse<>(content, records.getNumber(), records.getSize(),
                records.getTotalElements(), records.getTotalPages());
    }

    private String displayName(String ownerId) {
        return users.findById(UUID.fromString(ownerId)).map(UserAccount::getDisplayName).orElse("Unknown student");
    }

    private String projectTitle(UUID projectId) {
        return store.repository().findById(projectId).filter(record -> "idea".equals(record.getType()))
                .map(record -> store.parse(record).path("title").asText()).filter(title -> !title.isBlank())
                .orElse("");
    }
}
