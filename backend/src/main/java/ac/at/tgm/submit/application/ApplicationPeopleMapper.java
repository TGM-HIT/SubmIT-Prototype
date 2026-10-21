package ac.at.tgm.submit.application;

import ac.at.tgm.submit.user.StudentDetailsService;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.web.ApiDtos.StudentApplicationResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class ApplicationPeopleMapper {
    private final UserAccountRepository users;
    private final StudentDetailsService details;

    ApplicationPeopleMapper(UserAccountRepository users, StudentDetailsService details) {
        this.users = users;
        this.details = details;
    }

    String name(UUID id) {
        return users.findById(id).map(user -> user.getDisplayName()).filter(name -> !name.isBlank())
                .orElse("Unknown student");
    }

    StudentApplicationResponse staffDetails(StudentApplicationResponse response) {
        var student = users.findById(response.studentId()).filter(user -> "STUDENT".equals(user.getRole().name()))
                .map(details::response).orElse(null);
        return new StudentApplicationResponse(response.id(), response.studentId(), response.studentDisplayName(),
                response.periodId(), response.status(), response.preferences(),
                student == null ? null : student.className(),
                student == null ? null : student.electiveSubjectNames());
    }
}
