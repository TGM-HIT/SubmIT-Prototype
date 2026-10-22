package ac.at.tgm.submit.application;

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

    ApplicationPeriodLifecycle(ApplyingGroupRecords groups, AllocationService allocations) {
        this.groups = groups;
        this.allocations = allocations;
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
}
