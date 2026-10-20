package ac.at.tgm.submit.grading;

import static ac.at.tgm.submit.grading.GradingKeys.EFFECTIVE_FROM;
import static ac.at.tgm.submit.grading.GradingKeys.EFFECTIVE_TO;
import static ac.at.tgm.submit.grading.GradingKeys.ID;
import static ac.at.tgm.submit.grading.GradingKeys.NAME;
import static ac.at.tgm.submit.grading.GradingKeys.PUBLISHED;
import static ac.at.tgm.submit.grading.GradingKeys.PUBLISHED_AT;
import static ac.at.tgm.submit.grading.GradingKeys.PUBLISHED_BY;
import static ac.at.tgm.submit.grading.GradingKeys.RUBRIC;
import static ac.at.tgm.submit.grading.GradingKeys.SCHOOL_YEAR;
import static ac.at.tgm.submit.grading.GradingKeys.VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.audit.AuditService;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.grading.RubricDtos.PublishRubricRequest;
import ac.at.tgm.submit.grading.RubricDtos.RubricResponse;
import ac.at.tgm.submit.grading.RubricDtos.RubricSummary;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RubricService {
    private static final String ANY_ROLE = "hasAnyRole('STUDENT','TEACHER','ADMIN')";

    private final RecordStore store;
    private final AuditService audit;

    public RubricService(RecordStore store, AuditService audit) {
        this.store = store;
        this.audit = audit;
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public RubricResponse publish(AuthenticatedUser caller, PublishRubricRequest body) {
        if (caller.role() != Role.ADMIN) {
            throw new AccessDeniedException("Only administrators may publish rubric versions.");
        }
        ObjectNode rubric = RubricValidator.validate(GradingSupport.toNode(body));
        boolean duplicate = all().stream()
                .anyMatch(existing -> store.parse(existing).path(VERSION).asText().equals(rubric.path(VERSION).asText()));
        if (duplicate) {
            throw GradingSupport.conflict("Rubric version '" + rubric.path(VERSION).asText() + "' already exists.");
        }
        rubric.put(PUBLISHED_BY, caller.id().toString());
        rubric.put(PUBLISHED_AT, Instant.now().toString());
        StoredRecord record = store.create(RUBRIC, null, null, PUBLISHED, rubric);
        audit.record(caller, "RUBRIC_PUBLISHED", RUBRIC, record.getId());
        return GradingSupport.viewAs(store, record, RubricResponse.class);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(ANY_ROLE)
    public PageResponse<RubricSummary> list(AuthenticatedUser caller, int page, int size) {
        Pageable pageable = GradingSupport.newestFirst(page, size);
        Page<StoredRecord> records = store.page(RUBRIC, null, null, pageable);
        return new PageResponse<>(records.map(this::summary).getContent(), records.getNumber(), records.getSize(),
                records.getTotalElements(), records.getTotalPages());
    }

    @Transactional(readOnly = true)
    @PreAuthorize(ANY_ROLE)
    public RubricResponse active(AuthenticatedUser caller) {
        LocalDate today = LocalDate.now();
        return all().stream().filter(record -> isEffective(store.parse(record), today))
                .max(Comparator.comparing((StoredRecord record) -> store.parse(record).path(EFFECTIVE_FROM).asText())
                        .thenComparing(StoredRecord::getCreatedAt))
                .map(record -> GradingSupport.viewAs(store, record, RubricResponse.class))
                .orElseThrow(() -> new EntityNotFoundException("No rubric version is currently effective."));
    }

    @Transactional(readOnly = true)
    @PreAuthorize(ANY_ROLE)
    public RubricResponse get(AuthenticatedUser caller, UUID id) {
        return GradingSupport.viewAs(store, require(id), RubricResponse.class);
    }

    StoredRecord require(UUID id) {
        return store.get(RUBRIC, id);
    }

    boolean isEffective(JsonNode rubric, LocalDate day) {
        boolean started = !LocalDate.parse(rubric.path(EFFECTIVE_FROM).asText()).isAfter(day);
        boolean open = !rubric.hasNonNull(EFFECTIVE_TO) || !LocalDate.parse(rubric.path(EFFECTIVE_TO).asText()).isBefore(day);
        return started && open;
    }

    private List<StoredRecord> all() {
        return store.page(RUBRIC, null, null, Pageable.unpaged()).getContent();
    }

    private RubricSummary summary(StoredRecord record) {
        ObjectNode payload = store.parse(record);
        ObjectNode result = store.object();
        result.put(ID, record.getId().toString());
        for (String field : List.of(VERSION, NAME, SCHOOL_YEAR, EFFECTIVE_FROM, EFFECTIVE_TO, PUBLISHED_AT)) {
            if (payload.has(field)) {
                result.set(field, payload.get(field));
            }
        }
        return GradingSupport.convert(result, RubricSummary.class);
    }
}
