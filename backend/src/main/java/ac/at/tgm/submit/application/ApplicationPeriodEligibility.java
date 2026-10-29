package ac.at.tgm.submit.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.StudentClassRepository;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.web.ApiException;
import ac.at.tgm.submit.web.ApiDtos.EligibilityMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Resolves a period's effective eligible cohort from the current student/class assignments. */
@Component
public class ApplicationPeriodEligibility {
    private static final String ALL_STUDENTS = "ALL_STUDENTS";
    private static final String ELIGIBILITY_MODE = "eligibilityMode";
    private static final String ELIGIBLE_STUDENT_IDS = "eligibleStudentIds";
    private static final String ELIGIBLE_CLASS_IDS = "eligibleClassIds";
    private static final String SELECTED = "SELECTED";
    private final RecordStore store;
    private final UserAccountRepository users;
    private final StudentClassRepository classes;

    public ApplicationPeriodEligibility(
            RecordStore store, UserAccountRepository users, StudentClassRepository classes) {
        this.store = store;
        this.users = users;
        this.classes = classes;
    }

    public void validate(ObjectNode payload) {
        EligibilityIds ids = requestIds(payload);
        validateMode(payload.path(ELIGIBILITY_MODE).asText(), ids);
        validateReferences(ids);
        writeIds(payload, ids);
    }

    private EligibilityIds requestIds(ObjectNode payload) {
        String mode = payload.path(ELIGIBILITY_MODE).asText();
        if (!ALL_STUDENTS.equals(mode) && !SELECTED.equals(mode)) {
            throw badRequest("eligibilityMode must be ALL_STUDENTS or SELECTED.");
        }
        List<UUID> studentIds = uuidIds(payload.path(ELIGIBLE_STUDENT_IDS), ELIGIBLE_STUDENT_IDS);
        List<UUID> classIds = uuidIds(payload.path(ELIGIBLE_CLASS_IDS), ELIGIBLE_CLASS_IDS);
        return new EligibilityIds(mode, studentIds, classIds);
    }

    private void validateMode(String mode, EligibilityIds ids) {
        if (!unique(ids.studentIds()) || !unique(ids.classIds())) {
            throw badRequest("Eligibility IDs must be unique.");
        }
        if (ALL_STUDENTS.equals(mode) && (!ids.studentIds().isEmpty() || !ids.classIds().isEmpty())) {
            throw badRequest("ALL_STUDENTS mode must not include selected student or class IDs.");
        }
    }

    private boolean unique(List<UUID> ids) {
        return new HashSet<>(ids).size() == ids.size();
    }

    private void validateReferences(EligibilityIds ids) {
        List<UserAccount> selected = users.findAllById(ids.studentIds());
        if (selected.size() != ids.studentIds().size()
                || selected.stream().anyMatch(user -> !user.hasRole(Role.STUDENT))) {
            throw badRequest("Every selected student ID must reference a current STUDENT account.");
        }
        if (classes.findAllById(ids.classIds()).size() != ids.classIds().size()) {
            throw badRequest("Every selected class ID must reference an existing class.");
        }
    }

    private void writeIds(ObjectNode payload, EligibilityIds ids) {
        payload.set(ELIGIBLE_STUDENT_IDS, store.value(ids.studentIds().stream().map(UUID::toString).toList()));
        payload.set(ELIGIBLE_CLASS_IDS, store.value(ids.classIds().stream().map(UUID::toString).toList()));
    }

    private record EligibilityIds(String mode, List<UUID> studentIds, List<UUID> classIds) {
    }

    private List<UUID> uuidIds(JsonNode values, String field) {
        if (!values.isArray()) {
            throw badRequest(field + " must be an array.");
        }
        List<UUID> result = new java.util.ArrayList<>();
        for (JsonNode value : values) {
            try {
                if (!value.isTextual()) {
                    throw new IllegalArgumentException("Expected a UUID string.");
                }
                result.add(UUID.fromString(value.asText()));
            } catch (IllegalArgumentException exception) {
                throw badRequest(field + " must contain UUID values.");
            }
        }
        return result;
    }

    private ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    public ResponseFields responseFields(
            StoredRecord period, JsonNode data, UUID studentId, boolean adminView, boolean studentView) {
        return new ResponseFields(
                adminView ? EligibilityMode.valueOf(data.path(ELIGIBILITY_MODE).asText(ALL_STUDENTS)) : null,
                adminView ? responseIds(data.path(ELIGIBLE_STUDENT_IDS)) : null,
                adminView ? responseIds(data.path(ELIGIBLE_CLASS_IDS)) : null,
                studentView && studentId != null ? isEligible(period, studentId) : null);
    }

    private List<UUID> responseIds(JsonNode values) {
        if (!values.isArray()) {
            return List.of();
        }
        List<UUID> ids = new java.util.ArrayList<>();
        values.forEach(value -> ids.add(UUID.fromString(value.asText())));
        return List.copyOf(ids);
    }

    public record ResponseFields(
            EligibilityMode mode, List<UUID> studentIds, List<UUID> classIds, Boolean eligibleToApply) {
    }

    public List<UserAccount> eligibleStudents(StoredRecord period) {
        JsonNode data = store.parse(period);
        List<UserAccount> students = allCurrentStudents();
        if (!SELECTED.equals(data.path(ELIGIBILITY_MODE).asText(ALL_STUDENTS))) {
            return students;
        }

        Set<String> selectedStudents = stringIds(data.path(ELIGIBLE_STUDENT_IDS));
        Set<String> selectedClasses = stringIds(data.path(ELIGIBLE_CLASS_IDS));
        return students.stream().filter(student -> selectedStudents.contains(student.getId().toString())
                || student.getStudentClass() != null
                        && selectedClasses.contains(student.getStudentClass().getId().toString()))
                .toList();
    }

    private List<UserAccount> allCurrentStudents() {
        List<UserAccount> students = new java.util.ArrayList<>();
        int page = 0;
        Page<UserAccount> current;
        do {
            current = users.findByFilters(Role.STUDENT, null,
                    PageRequest.of(page++, 100, Sort.by(Sort.Direction.ASC, "id")));
            students.addAll(current.getContent());
        } while (current.hasNext());
        return List.copyOf(students);
    }

    public boolean isEligible(StoredRecord period, UUID studentId) {
        JsonNode data = store.parse(period);
        UserAccount student = users.findById(studentId).orElse(null);
        if (student == null || !student.hasRole(Role.STUDENT)) {
            return false;
        }
        if (!SELECTED.equals(data.path(ELIGIBILITY_MODE).asText(ALL_STUDENTS))) {
            return true;
        }
        if (stringIds(data.path(ELIGIBLE_STUDENT_IDS)).contains(studentId.toString())) {
            return true;
        }
        return student.getStudentClass() != null
                && stringIds(data.path(ELIGIBLE_CLASS_IDS)).contains(student.getStudentClass().getId().toString());
    }

    private Set<String> stringIds(JsonNode values) {
        Set<String> result = new HashSet<>();
        if (values.isArray()) {
            values.forEach(value -> result.add(value.asText()));
        }
        return result;
    }
}
