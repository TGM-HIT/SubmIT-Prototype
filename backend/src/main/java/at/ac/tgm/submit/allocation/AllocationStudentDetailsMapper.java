package at.ac.tgm.submit.allocation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.security.Role;
import at.ac.tgm.submit.user.StudentDetailsService;
import at.ac.tgm.submit.user.UserAccountRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
class AllocationStudentDetailsMapper {
    private final UserAccountRepository users;
    private final StudentDetailsService details;

    AllocationStudentDetailsMapper(UserAccountRepository users, StudentDetailsService details) {
        this.users = users;
        this.details = details;
    }

    void addStudentDetails(JsonNode entries) {
        if (!entries.isArray()) {
            return;
        }
        entries.forEach(entry -> {
            if (entry instanceof ObjectNode object) {
                UUID id = parseId(entry.path("studentId").asText());
                if (id != null) {
                    users.findById(id).filter(user -> user.hasRole(Role.STUDENT))
                            .ifPresent(user -> {
                                var student = details.response(user);
                                if (student.className() != null) {
                                    object.put("className", student.className());
                                }
                                ArrayNode subjects = object.putArray("electiveSubjectNames");
                                student.electiveSubjectNames().forEach(subjects::add);
                            });
                }
            }
        });
    }

    private UUID parseId(String id) {
        try {
            return UUID.fromString(id);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
