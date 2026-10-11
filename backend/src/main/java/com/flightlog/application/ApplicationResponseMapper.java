package com.flightlog.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.user.UserAccount;
import com.flightlog.user.UserAccountRepository;
import com.flightlog.web.ApiDtos.ApplicationPeriodResponse;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiDtos.PreferenceResponse;
import com.flightlog.web.ApiDtos.ProjectTitleResponse;
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
        return period(record, false);
    }

    ApplicationPeriodResponse period(StoredRecord record, boolean studentView) {
        ObjectNode data = store.parse(record);
        List<UUID> projects = new ArrayList<>();
        data.path(PROJECT_IDS).forEach(project -> projects.add(UUID.fromString(project.asText())));
        return new ApplicationPeriodResponse(record.getId(), data.path("schoolYear").asText(),
                Instant.parse(data.path(OPENS_AT).asText()), Instant.parse(data.path(CLOSES_AT).asText()),
                record.getStatus(), List.copyOf(projects), studentView ? projectTitles(record) : null);
    }

    StudentApplicationResponse studentApplication(StoredRecord record, Map<UUID, String> projectTitles) {
        return application(record, displayName(record.getOwnerId()), projectTitles);
    }

    private StudentApplicationResponse application(StoredRecord record, String displayName,
            Map<UUID, String> projectTitles) {
        ObjectNode payload = store.parse(record);
        List<PreferenceResponse> preferences = new ArrayList<>();
        payload.path("preferences").forEach(preference -> {
            UUID projectId = UUID.fromString(preference.path("projectId").asText());
            String title = projectTitles.getOrDefault(projectId, "Unknown project");
            preferences.add(new PreferenceResponse(projectId, preference.path("rank").asInt(),
                    title.isBlank() ? "Unknown project" : title));
        });
        return new StudentApplicationResponse(record.getId(), UUID.fromString(record.getOwnerId()), displayName,
                UUID.fromString(record.getParentId()), record.getStatus(), List.copyOf(preferences));
    }

    PageResponse<ApplicationPeriodResponse> periods(Page<StoredRecord> records, boolean studentView) {
        return new PageResponse<>(records.map(record -> period(record, studentView)).getContent(), records.getNumber(),
                records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    Map<UUID, String> studentProjectTitles(StoredRecord period) {
        ApplicationPeriodResponse response = period(period, true);
        Map<UUID, String> titles = new HashMap<>();
        if (response.projectTitles() != null) {
            response.projectTitles().forEach(project -> titles.put(project.projectId(), project.title()));
        }
        return titles;
    }

    private List<ProjectTitleResponse> projectTitles(StoredRecord period) {
        ObjectNode payload = store.parse(period);
        Instant now = Instant.now();
        Instant opensAt = Instant.parse(payload.path(OPENS_AT).asText());
        Instant closesAt = Instant.parse(payload.path(CLOSES_AT).asText());
        if (!"OPEN".equals(period.getStatus()) || now.isBefore(opensAt) || now.isAfter(closesAt)) {
            return null;
        }
        List<ProjectTitleResponse> titles = new ArrayList<>();
        payload.path(PROJECT_IDS).forEach(project -> {
            UUID projectId = UUID.fromString(project.asText());
            String title = store.repository().findById(projectId)
                    .filter(record -> "idea".equals(record.getType()))
                    .filter(record -> "ACCEPTED_FOR_APPLICATIONS".equals(record.getStatus()))
                    .map(record -> store.parse(record).path("title").asText())
                    .filter(value -> !value.isBlank())
                    .orElse("Unknown project");
            titles.add(new ProjectTitleResponse(projectId, title));
        });
        return List.copyOf(titles);
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

}
