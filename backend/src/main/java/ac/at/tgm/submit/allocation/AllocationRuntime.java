package ac.at.tgm.submit.allocation;

import com.fasterxml.jackson.databind.JsonNode;
import ac.at.tgm.submit.application.ApplicationPeriodEligibility;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Shared clock and account lookups used while processing and presenting allocation runs. */
@Component
class AllocationRuntime {
    private final UserAccountRepository users;
    private final AllocationStudentDetailsMapper studentDetails;
    private final Clock clock;
    private final ApplicationPeriodEligibility eligibility;

    AllocationRuntime(UserAccountRepository users, AllocationStudentDetailsMapper studentDetails, Clock clock,
            ApplicationPeriodEligibility eligibility) {
        this.users = users;
        this.studentDetails = studentDetails;
        this.clock = clock;
        this.eligibility = eligibility;
    }

    Instant now() {
        return Instant.now(clock);
    }

    boolean isStudent(UUID id) {
        return users.findById(id).filter(user -> user.getRole() == Role.STUDENT).isPresent();
    }

    List<ac.at.tgm.submit.user.UserAccount> eligibleStudents(StoredRecord period) {
        return eligibility.eligibleStudents(period);
    }

    Map<UUID, String> displayNames(Collection<UUID> studentIds) {
        Map<UUID, String> names = new HashMap<>();
        users.findAllById(studentIds).forEach(user -> names.put(user.getId(), user.getDisplayName()));
        return names;
    }

    void addStudentDetails(JsonNode entries) {
        studentDetails.addStudentDetails(entries);
    }
}
