package com.flightlog.grading;

import static com.flightlog.grading.GradingKeys.MAX;
import static com.flightlog.grading.GradingKeys.MEASURED_AT;
import static com.flightlog.grading.GradingKeys.METRIC;
import static com.flightlog.grading.GradingKeys.METRIC_ID;
import static com.flightlog.grading.GradingKeys.METRIC_VERSION;
import static com.flightlog.grading.GradingKeys.MIN;
import static com.flightlog.grading.GradingKeys.NOTE;
import static com.flightlog.grading.GradingKeys.RECORDED_AT;
import static com.flightlog.grading.GradingKeys.RECORDED_BY;
import static com.flightlog.grading.GradingKeys.RUBRIC_ID;
import static com.flightlog.grading.GradingKeys.RUBRIC_VERSION;
import static com.flightlog.grading.GradingKeys.VALUE;
import static com.flightlog.grading.GradingKeys.VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.audit.AuditService;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.grading.GradeDtos.MetricRequest;
import com.flightlog.grading.GradeDtos.MetricResponse;
import com.flightlog.security.AuthenticatedUser;
import com.flightlog.web.ApiDtos.PageResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MetricService {
    private final RecordStore store;
    private final AuditService audit;
    private final ProjectAccess access;
    private final RubricBinding binding;

    public MetricService(RecordStore store, AuditService audit, ProjectAccess access, RubricBinding binding) {
        this.store = store;
        this.audit = audit;
        this.access = access;
        this.binding = binding;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public MetricResponse record(AuthenticatedUser caller, UUID projectId, MetricRequest body) {
        access.requireTeacher(caller, projectId);
        JsonNode request = GradingSupport.toNode(body);
        RubricRef rubric = binding.bind(caller, projectId, request);
        String metricId = GradingSupport.text(request, METRIC_ID);
        JsonNode definition = RubricModel.metric(rubric.payload(), metricId)
                .orElseThrow(() -> GradingSupport.bad("Metric '" + metricId + "' is not defined in this rubric version."));
        BigDecimal value = GradingSupport.number(request, VALUE);
        checkBounds(value, definition);
        ObjectNode payload = store.object();
        payload.put(RUBRIC_ID, rubric.id().toString());
        payload.put(RUBRIC_VERSION, rubric.payload().path(VERSION).asText());
        payload.put(METRIC_ID, metricId);
        payload.put(METRIC_VERSION, definition.path(VERSION).asInt());
        payload.put(VALUE, value);
        payload.put(MEASURED_AT, GradingSupport.instant(request, MEASURED_AT, Instant.now()).toString());
        payload.put(RECORDED_BY, caller.id().toString());
        payload.put(RECORDED_AT, Instant.now().toString());
        String note = GradingSupport.optionalText(request, NOTE);
        if (note != null) {
            payload.put(NOTE, note);
        }
        StoredRecord record = store.create(METRIC, caller.id().toString(), projectId.toString(),
                GradingKeys.ACTIVE, payload);
        audit.record(caller, "METRIC_RECORDED", METRIC, record.getId());
        return GradingSupport.viewAs(store, record, MetricResponse.class);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public PageResponse<MetricResponse> list(AuthenticatedUser caller, UUID projectId, int page, int size) {
        access.requireTeacherOrMember(caller, projectId);
        return GradingSupport.pageOf(store,
                store.page(METRIC, null, projectId.toString(), GradingSupport.newestFirst(page, size)), MetricResponse.class);
    }

    private void checkBounds(BigDecimal value, JsonNode definition) {
        if (definition.hasNonNull(MIN) && value.compareTo(definition.path(MIN).decimalValue()) < 0
                || definition.hasNonNull(MAX) && value.compareTo(definition.path(MAX).decimalValue()) > 0) {
            throw GradingSupport.bad("The metric value is outside the configured range.");
        }
    }
}
