package ac.at.tgm.submit.user;

import ac.at.tgm.submit.audit.AuditService;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.web.ApiDtos.CatalogueEntryRequest;
import ac.at.tgm.submit.web.ApiDtos.CatalogueEntryResponse;
import ac.at.tgm.submit.web.ApiDtos.PageResponse;
import ac.at.tgm.submit.web.ApiDtos.StudentDetailsRequest;
import ac.at.tgm.submit.web.ApiDtos.StudentDetailsResponse;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ac.at.tgm.submit.web.ApiException;

@Service
public class StudentDetailsService {
    private static final int MAX_ELECTIVE_SUBJECTS = 4;
    private final StudentClassRepository classes;
    private final ElectiveSubjectRepository subjects;
    private final UserAccountRepository users;
    private final AuditService audit;

    public StudentDetailsService(StudentClassRepository classes, ElectiveSubjectRepository subjects,
            UserAccountRepository users, AuditService audit) {
        this.classes = classes;
        this.subjects = subjects;
        this.users = users;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogueEntryResponse> classes(AuthenticatedUser caller, int page, int size) {
        requireStaff(caller);
        Page<StudentClassEntity> results = classes.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new PageResponse<>(results.map(value -> new CatalogueEntryResponse(value.getId(), value.getName()))
                .getContent(), results.getNumber(), results.getSize(), results.getTotalElements(),
                results.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogueEntryResponse> subjects(AuthenticatedUser caller, int page, int size) {
        requireStaff(caller);
        Page<ElectiveSubjectEntity> results = subjects.findAll(PageRequest.of(page, size, Sort.by("name")));
        return new PageResponse<>(results.map(value -> new CatalogueEntryResponse(value.getId(), value.getName()))
                .getContent(), results.getNumber(), results.getSize(), results.getTotalElements(),
                results.getTotalPages());
    }

    @Transactional
    public CatalogueEntryResponse createClass(AuthenticatedUser caller, CatalogueEntryRequest request) {
        requireAdmin(caller);
        String name = normalizedName(request.name());
        rejectDuplicateClassName(name, null);
        StudentClassEntity entry = classes.save(new StudentClassEntity(UUID.randomUUID(), name));
        audit.record(caller, "STUDENT_CLASS_CREATED", "student_class", entry.getId());
        return new CatalogueEntryResponse(entry.getId(), entry.getName());
    }

    @Transactional
    public CatalogueEntryResponse renameClass(AuthenticatedUser caller, UUID id, CatalogueEntryRequest request) {
        requireAdmin(caller);
        StudentClassEntity entry = classes.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Class was not found."));
        String name = normalizedName(request.name());
        rejectDuplicateClassName(name, id);
        entry.setName(name);
        audit.record(caller, "STUDENT_CLASS_RENAMED", "student_class", id);
        return new CatalogueEntryResponse(id, name);
    }

    @Transactional
    public CatalogueEntryResponse createSubject(AuthenticatedUser caller, CatalogueEntryRequest request) {
        requireAdmin(caller);
        String name = normalizedName(request.name());
        rejectDuplicateSubjectName(name, null);
        ElectiveSubjectEntity entry = subjects.save(new ElectiveSubjectEntity(UUID.randomUUID(), name));
        audit.record(caller, "ELECTIVE_SUBJECT_CREATED", "elective_subject", entry.getId());
        return new CatalogueEntryResponse(entry.getId(), entry.getName());
    }

    @Transactional
    public CatalogueEntryResponse renameSubject(AuthenticatedUser caller, UUID id, CatalogueEntryRequest request) {
        requireAdmin(caller);
        ElectiveSubjectEntity entry = subjects.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Subject was not found."));
        String name = normalizedName(request.name());
        rejectDuplicateSubjectName(name, id);
        entry.setName(name);
        audit.record(caller, "ELECTIVE_SUBJECT_RENAMED", "elective_subject", id);
        return new CatalogueEntryResponse(id, name);
    }

    @Transactional(readOnly = true)
    public StudentDetailsResponse details(AuthenticatedUser caller, UUID studentId) {
        requireStaff(caller);
        UserAccount student = findStudent(studentId);
        return response(student);
    }

    @Transactional
    public StudentDetailsResponse assign(AuthenticatedUser caller, UUID studentId, StudentDetailsRequest request) {
        requireAdmin(caller);
        UserAccount student = users.findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student was not found."));
        if (!student.hasRole(Role.STUDENT)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Assignments can only target student accounts.");
        }
        List<UUID> subjectIds = request.electiveSubjectIds() == null ? List.of() : request.electiveSubjectIds();
        if (subjectIds.size() > MAX_ELECTIVE_SUBJECTS) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "A student can have at most four elective subjects.");
        }
        if (new HashSet<>(subjectIds).size() != subjectIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Elective subject assignments must be unique.");
        }
        StudentClassEntity studentClass = classes.findById(request.classId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "The selected class was not found."));
        List<ElectiveSubjectEntity> assignedSubjects = subjects.findAllById(subjectIds);
        if (assignedSubjects.size() != subjectIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "One or more selected subjects were not found.");
        }
        student.setStudentClass(studentClass);
        student.setElectiveSubjects(assignedSubjects);
        student.setRole(Role.STUDENT);
        audit.record(caller, "STUDENT_DETAILS_ASSIGNED", "user", studentId);
        return response(student);
    }

    public StudentDetailsResponse response(UserAccount student) {
        String className = student.getStudentClass() == null ? null : student.getStudentClass().getName();
        List<ElectiveSubjectEntity> orderedSubjects = student.getElectiveSubjects().stream()
                .sorted(java.util.Comparator.comparing(ElectiveSubjectEntity::getName,
                        String.CASE_INSENSITIVE_ORDER).thenComparing(ElectiveSubjectEntity::getId))
                .toList();
        List<UUID> subjectIds = orderedSubjects.stream().map(ElectiveSubjectEntity::getId).toList();
        return new StudentDetailsResponse(student.getId(), student.getDisplayName(),
                student.getStudentClass() == null ? null : student.getStudentClass().getId(),
                className, subjectIds, orderedSubjects.stream().map(ElectiveSubjectEntity::getName).toList());
    }

    private UserAccount findStudent(UUID studentId) {
        UserAccount student = users.findById(studentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student was not found."));
        if (!student.hasRole(Role.STUDENT)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Assignments can only target student accounts.");
        }
        return student;
    }

    private String normalizedName(String value) {
        String name = value.strip();
        if (name.isEmpty() || name.length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Catalogue names must contain 1 to 100 characters.");
        }
        return name;
    }

    private void rejectDuplicateClassName(String name, UUID exceptId) {
        boolean exists = classes.findAll().stream()
                .anyMatch(entry -> !entry.getId().equals(exceptId) && entry.getName().equalsIgnoreCase(name));
        if (exists) {
            throw new ApiException(HttpStatus.CONFLICT, "A class with that name already exists.");
        }
    }

    private void rejectDuplicateSubjectName(String name, UUID exceptId) {
        boolean exists = subjects.findAll().stream()
                .anyMatch(entry -> !entry.getId().equals(exceptId) && entry.getName().equalsIgnoreCase(name));
        if (exists) {
            throw new ApiException(HttpStatus.CONFLICT, "A subject with that name already exists.");
        }
    }

    private void requireStaff(AuthenticatedUser caller) {
        if (caller.hasRole(Role.STUDENT)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This operation requires a teacher or administrator.");
        }
    }

    private void requireAdmin(AuthenticatedUser caller) {
        if (!caller.hasRole(Role.ADMIN)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "This operation requires the ADMIN role.");
        }
    }
}
