package com.flightlog.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.web.ApiDtos.ApplicationPeriodResponse;
import com.flightlog.web.ApiDtos.PageResponse;
import com.flightlog.web.ApiDtos.PreferenceResponse;
import com.flightlog.web.ApiDtos.StudentApplicationResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
class ApplicationResponseMapper {
    private static final String PROJECT_IDS = "projectIds";
    private static final String OPENS_AT = "opensAt";
    private static final String CLOSES_AT = "closesAt";

    private final RecordStore store;

    ApplicationResponseMapper(RecordStore store) {
        this.store = store;
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
        ObjectNode payload = store.parse(record);
        List<PreferenceResponse> preferences = new ArrayList<>();
        payload.path("preferences").forEach(preference -> preferences.add(new PreferenceResponse(
                UUID.fromString(preference.path("projectId").asText()), preference.path("rank").asInt())));
        return new StudentApplicationResponse(record.getId(), UUID.fromString(record.getOwnerId()),
                UUID.fromString(record.getParentId()), record.getStatus(), List.copyOf(preferences));
    }

    PageResponse<ApplicationPeriodResponse> periods(Page<StoredRecord> records) {
        return new PageResponse<>(records.map(this::period).getContent(), records.getNumber(),
                records.getSize(), records.getTotalElements(), records.getTotalPages());
    }

    PageResponse<StudentApplicationResponse> applications(Page<StoredRecord> records) {
        return new PageResponse<>(records.map(this::application).getContent(), records.getNumber(),
                records.getSize(), records.getTotalElements(), records.getTotalPages());
    }
}
