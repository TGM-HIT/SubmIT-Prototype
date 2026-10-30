package at.ac.tgm.submit.application;

import com.fasterxml.jackson.databind.JsonNode;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.user.StudentDetailsService;
import at.ac.tgm.submit.user.UserAccountRepository;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.web.ApiDtos.StudentApplicationResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ApplicationPeopleMapper {
    private final UserAccountRepository users;
    private final StudentDetailsService details;
    private final ApplicationPeriodEligibility eligibility;

    ApplicationPeopleMapper(UserAccountRepository users, StudentDetailsService details,
            ApplicationPeriodEligibility eligibility) {
        this.users = users;
        this.details = details;
        this.eligibility = eligibility;
    }

    String name(UUID id) {
        return users.findById(id).map(user -> user.getDisplayName()).filter(name -> !name.isBlank())
                .orElse("Unknown student");
    }

    StudentApplicationResponse staffDetails(StudentApplicationResponse response) {
        var student = users.findById(response.studentId()).filter(user -> user.hasRole(Role.STUDENT))
                .map(details::response).orElse(null);
        return new StudentApplicationResponse(response.id(), response.studentId(), response.studentDisplayName(),
                response.periodId(), response.status(), response.preferences(),
                student == null ? null : student.className(),
                student == null ? null : student.electiveSubjectNames());
    }

    ApplicationPeriodEligibility.ResponseFields periodEligibility(
            StoredRecord period, JsonNode data, UUID studentId, boolean adminView, boolean studentView) {
        return eligibility.responseFields(period, data, studentId, adminView, studentView);
    }
}
