package ac.at.tgm.submit.application;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.allocation.AllocationService;
import ac.at.tgm.submit.data.StoredRecord;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Coordinates the application-period boundary and its required initial allocation. */
@Component
class ApplicationPeriodLifecycle {
    private final ApplyingGroupRecords groups;
    private final AllocationService allocations;
    private final ApplicationPeriodEligibility eligibility;

    ApplicationPeriodLifecycle(ApplyingGroupRecords groups, AllocationService allocations,
            ApplicationPeriodEligibility eligibility) {
        this.groups = groups;
        this.allocations = allocations;
        this.eligibility = eligibility;
    }

    Instant now() {
        return groups.now();
    }

    void rejectSubmittedSoloWrite(UUID periodId, String studentId) {
        groups.rejectSubmittedSoloWrite(periodId, studentId);
    }

    StoredRecord acceptedGroup(UUID periodId, String studentId) {
        return groups.accepted(periodId, studentId);
    }

    void ensureInitialRunIfDue(StoredRecord period) {
        allocations.ensureInitialRunIfDue(period);
    }

    void validateEligibility(ObjectNode payload) {
        eligibility.validate(payload);
    }

    boolean isEligible(StoredRecord period, UUID studentId) {
        return eligibility.isEligible(period, studentId);
    }

}
