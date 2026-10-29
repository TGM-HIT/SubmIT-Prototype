package ac.at.tgm.submit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.web.ApiException;
import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Shared queries and validation. Writers serialize on the owning period row. */
@Component
public class ApplyingGroupRecords {
    public static final String TYPE = "applying_group";
    public static final int MAX_MEMBERS = 5;
    private final RecordStore store;
    private final UserAccountRepository users;
    private final ApplicationPeriodEligibility eligibility;
    private final Clock clock;

    public ApplyingGroupRecords(RecordStore store, UserAccountRepository users,
            ApplicationPeriodEligibility eligibility, Clock clock) {
        this.store = store;
        this.users = users;
        this.eligibility = eligibility;
        this.clock = clock;
    }

    public List<StoredRecord> groups(UUID periodId) {
        List<StoredRecord> result = new ArrayList<>();
        int page = 0;
        org.springframework.data.domain.Page<StoredRecord> records;
        do {
            records = store.page(TYPE, null, periodId.toString(),
                    PageRequest.of(page++, 100, Sort.by(Sort.Direction.ASC, "createdAt", "id")));
            result.addAll(records.getContent());
        } while (records.hasNext());
        return result;
    }

    public StoredRecord accepted(UUID periodId, String studentId) {
        return groups(periodId).stream().filter(group -> !"DISSOLVED".equals(group.getStatus()))
                .filter(group -> ids(store.parse(group).path("members")).contains(studentId))
                .findFirst().orElse(null);
    }

    public void rejectSubmittedSoloWrite(UUID periodId, String studentId) {
        StoredRecord group = accepted(periodId, studentId);
        if (group != null && "SUBMITTED".equals(group.getStatus())) {
            throw conflict("A submitted shared application represents you. Explicitly withdraw or leave it first.");
        }
    }

    public StoredRecord period(UUID periodId, boolean lock) {
        if (lock) {
            return store.lock("application_period", periodId);
        }
        return store.repository().findById(periodId)
                .filter(record -> "application_period".equals(record.getType()))
                .orElseThrow(() -> new EntityNotFoundException("Application period was not found."));
    }

    public boolean locked(StoredRecord period) {
        ObjectNode data = store.parse(period);
        Instant now = now();
        return "FINALIZED".equals(period.getStatus())
                || now.isBefore(Instant.parse(data.path("opensAt").asText()))
                || !now.isBefore(Instant.parse(data.path("closesAt").asText()));
    }

    public Instant now() {
        return Instant.now(clock);
    }

    public void requireOpen(StoredRecord period) {
        if (locked(period)) {
            throw conflict("Applying groups are locked at the deadline or when the period is not open.");
        }
    }

    public void student(String id) {
        if (users.findById(UUID.fromString(id)).filter(user -> user.hasRole(Role.STUDENT)).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Every accepted member must currently be a student.");
        }
    }

    public List<String> ids(JsonNode node) {
        List<String> result = new ArrayList<>();
        node.forEach(value -> result.add(value.asText()));
        return result;
    }

    public void validateRanking(StoredRecord period, List<String> projects) {
        if (projects.size() < 3 || projects.stream().distinct().count() != projects.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Rank at least three distinct catalogue projects.");
        }

        List<String> catalogue = ids(store.parse(period).path("projectIds"));
        if (!catalogue.containsAll(projects)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Every preference must belong to this period's catalogue.");
        }
        for (String id : projects) {
            if (store.repository().findById(UUID.fromString(id))
                    .filter(record -> "idea".equals(record.getType()))
                    .filter(record -> "ACCEPTED_FOR_APPLICATIONS".equals(record.getStatus())).isEmpty()) {
                throw conflict("A ranked project is no longer accepted for applications.");
            }
        }

    }

    public void requireEligibleMembers(StoredRecord period, List<String> members) {
        for (String member : members) {
            if (!eligibility.isEligible(period, UUID.fromString(member))) {
                throw conflict("Every accepted member must be eligible for this application period.");
            }
        }
    }

    public void validatePins(UUID periodId) {
        var effective = new java.util.HashMap<String, String>();
        for (StoredRecord pin : store.repository().findByTypeAndParentId("allocation_pin", periodId.toString())) {
            String project = store.parse(pin).path("projectId").asText();
            StoredRecord group = accepted(periodId, pin.getOwnerId());
            mergePin(effective, pin, group, project);
        }
        for (String project : effective.values().stream().distinct().toList()) {
            if (effective.values().stream().filter(project::equals).count() > MAX_MEMBERS) {
                throw conflict("A project can have at most five effectively pinned students.");
            }
        }
    }

    private void mergePin(java.util.Map<String, String> effective, StoredRecord pin, StoredRecord group, String project) {
        List<String> members = group != null && "SUBMITTED".equals(group.getStatus())
                ? ids(store.parse(group).path("members")) : List.of(pin.getOwnerId());
        for (String member : members) {
            String previous = effective.putIfAbsent(member, project);
            if (previous != null && !previous.equals(project)) {
                throw conflict("Members of a submitted applying group cannot have conflicting pins.");
            }
        }
    }

    public static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }
}
