package ac.at.tgm.submit.grading;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.grading.FinalGradeDtos.Decision;
import ac.at.tgm.submit.grading.FinalGradeDtos.FinalGradeHistory;
import ac.at.tgm.submit.grading.FinalGradeDtos.FinalGradesRequest;
import ac.at.tgm.submit.grading.FinalGradeDtos.FinalGradesResponse;
import ac.at.tgm.submit.grading.FinalGradeDtos.StudentDecision;
import ac.at.tgm.submit.grading.GradeDtos.GradeResponse;
import ac.at.tgm.submit.grading.GradeDtos.GradeRevisionRequest;
import ac.at.tgm.submit.grading.GradeDtos.GroupGradeRequest;
import ac.at.tgm.submit.grading.GradeDtos.MetricRequest;
import ac.at.tgm.submit.grading.GradeDtos.MetricResponse;
import ac.at.tgm.submit.grading.GradeDtos.StudentGradeRequest;
import ac.at.tgm.submit.grading.GradeDtos.VersionTransition;
import ac.at.tgm.submit.grading.RubricDtos.AggregationRuleDto;
import ac.at.tgm.submit.grading.RubricDtos.CategoryDto;
import ac.at.tgm.submit.grading.RubricDtos.CriterionDto;
import ac.at.tgm.submit.grading.RubricDtos.MetricDefinitionDto;
import ac.at.tgm.submit.grading.RubricDtos.PublishRubricRequest;
import ac.at.tgm.submit.grading.RubricDtos.RubricResponse;
import ac.at.tgm.submit.grading.RubricDtos.ScaleDto;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.Role;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import ac.at.tgm.submit.user.StudentClassEntity;
import ac.at.tgm.submit.user.StudentClassRepository;
import ac.at.tgm.submit.user.ElectiveSubjectEntity;
import ac.at.tgm.submit.user.ElectiveSubjectRepository;
import ac.at.tgm.submit.web.ApiException;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest
class GradingServiceTest {
    @Autowired
    private RecordStore store;
    @Autowired
    private RubricService rubrics;
    @Autowired
    private GradeService grades;
    @Autowired
    private MetricService metrics;
    @Autowired
    private FinalGradeService finalGrades;
    @Autowired private UserAccountRepository users;
    @Autowired private StudentClassRepository classes;
    @Autowired private ElectiveSubjectRepository subjects;

    private final AuthenticatedUser admin = user(Role.ADMIN);
    private final AuthenticatedUser teacher = user(Role.TEACHER);
    private final AuthenticatedUser alice = user(Role.STUDENT);
    private final AuthenticatedUser bob = user(Role.STUDENT);
    private final AuthenticatedUser outsider = user(Role.STUDENT);
    private UUID projectId;
    private UUID rubricId;

    private static AuthenticatedUser user(Role role) {
        return new AuthenticatedUser(UUID.randomUUID(), role.name().toLowerCase(), role);
    }

    private void login(AuthenticatedUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(user, null, "ROLE_" + user.role().name()));
    }

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        UserAccount aliceAccount = new UserAccount(alice.id(), "alice-" + alice.id(), "Alice Student", "unused", Role.STUDENT);
        StudentClassEntity studentClass = classes.save(new StudentClassEntity(UUID.randomUUID(), "Class " + UUID.randomUUID()));
        ElectiveSubjectEntity subject = subjects.save(new ElectiveSubjectEntity(UUID.randomUUID(), "Subject " + UUID.randomUUID()));
        aliceAccount.setStudentClass(studentClass);
        aliceAccount.setElectiveSubjects(List.of(subject));
        users.save(aliceAccount);
        users.save(new UserAccount(bob.id(), "bob-" + bob.id(), "Bob Student", "unused", Role.STUDENT));
        ObjectNode group = store.object();
        group.putArray("studentIds").add(alice.id().toString()).add(bob.id().toString());
        store.create("project_group", null, projectId.toString(), "ALLOCATED", group);
        login(admin);
        rubricId = rubrics.publish(admin, rubricRequest(UUID.randomUUID().toString())).id();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static BigDecimal n(int value) {
        return BigDecimal.valueOf(value);
    }

    private static PublishRubricRequest rubricRequest(String version) {
        ScaleDto scale = new ScaleDto(n(1), n(5), null, null);
        return new PublishRubricRequest(version, "Test", null, LocalDate.now().minusDays(1).toString(), null, scale,
                List.of(new CategoryDto("tech", "Technical", null, List.of(
                        new CriterionDto("impl", "Implementation", null, "STUDENT", null, false, scale),
                        new CriterionDto("team", "Teamwork", null, "GROUP", null, true, scale)))),
                List.of(new MetricDefinitionDto("hours", "Hours", 1, null, null, n(0), null)),
                List.of(new AggregationRuleDto("avg", 2, "STUDENT", "WEIGHTED_AVERAGE", 1, Map.of("impl", n(3), "team", n(1)),
                        null)));
    }

    private StudentGradeRequest studentGrade(AuthenticatedUser student, int grade) {
        return studentGrade(student, rubricId, grade, null);
    }

    private static StudentGradeRequest studentGrade(AuthenticatedUser student, UUID rubric, int grade,
            VersionTransition transition) {
        return new StudentGradeRequest(rubric, transition, student.id(), "impl", n(grade), null, null, null);
    }

    private GroupGradeRequest teamGrade(int grade, String evidence) {
        return new GroupGradeRequest(rubricId, null, "team", n(grade), null, evidence, null);
    }

    private StudentDecision byRule(AuthenticatedUser student, Integer grade) {
        return new StudentDecision(student.id(), "avg", grade == null ? null : n(grade), null);
    }

    @Test
    void requestContractsRejectInvalidInputBeforeReachingTheService() {
        var validator = Validation.buildDefaultValidatorFactory().getValidator();
        assertTrue(validator.validate(new PublishRubricRequest("", "n", null, "bad-date", null, null, List.of(), null, null))
                .size() >= 4);
        assertTrue(validator.validate(new StudentGradeRequest(null, null, null, " ", null, null, null, null)).size() >= 4);
        assertTrue(validator.validate(rubricRequest("v")).isEmpty());
    }

    @Test
    void onlyAdminsPublishAndVersionsAreExposedAsData() {
        login(teacher);
        assertThrows(AccessDeniedException.class, () -> rubrics.publish(teacher, rubricRequest("x")));
        login(alice);
        RubricResponse rubric = rubrics.get(alice, rubricId);
        assertEquals(2, rubric.categories().get(0).criteria().size());
        assertEquals(1, rubric.metricDefinitions().size());
    }

    @Test
    void gradesAreValidatedAgainstConfiguredScaleAndRevisionsRetainHistory() {
        login(teacher);
        ApiException invalid = assertThrows(ApiException.class,
                () -> grades.recordStudentGrade(teacher, projectId, studentGrade(alice, 6)));
        assertEquals(400, invalid.getStatus().value());
        GradeResponse first = grades.recordStudentGrade(teacher, projectId, studentGrade(alice, 4));
        GradeResponse revision = grades.reviseStudentGrade(teacher, projectId, first.id(),
                new GradeRevisionRequest("Reassessed after defense", n(2), null, null, null));
        assertEquals(first.id(), revision.supersedesId());
        assertEquals(2, grades.listStudentGrades(teacher, projectId, null, 0, 20).totalElements());
        assertThrows(ApiException.class, () -> grades.reviseStudentGrade(teacher, projectId, first.id(),
                new GradeRevisionRequest("again", n(3), null, null, null)));
    }

    @Test
    void staffGradeResponsesResolveCurrentStudentIdentityButStudentResponsesDoNot() {
        login(teacher);
        GradeResponse recorded = grades.recordStudentGrade(teacher, projectId, studentGrade(alice, 3));
        assertEquals("Alice Student", recorded.studentDisplayName());
        assertTrue(recorded.className().startsWith("Class "));
        assertEquals(1, recorded.electiveSubjectNames().size());
        login(alice);
        GradeResponse own = grades.listStudentGrades(alice, projectId, alice.id().toString(), 0, 20).content().get(0);
        assertEquals(null, own.studentDisplayName());
        assertEquals(null, own.className());
        assertEquals(null, own.electiveSubjectNames());
    }

    @Test
    void studentsReadOnlyOwnGradesAndOutsidersAreDenied() {
        login(teacher);
        grades.recordStudentGrade(teacher, projectId, studentGrade(alice, 1));
        grades.recordStudentGrade(teacher, projectId, studentGrade(bob, 2));
        login(alice);
        assertEquals(1, grades.listStudentGrades(alice, projectId, null, 0, 20).totalElements());
        assertThrows(AccessDeniedException.class,
                () -> grades.listStudentGrades(alice, projectId, bob.id().toString(), 0, 20));
        assertThrows(AccessDeniedException.class, () -> grades.recordStudentGrade(alice, projectId, studentGrade(alice, 1)));
        login(outsider);
        assertThrows(AccessDeniedException.class, () -> grades.listStudentGrades(outsider, projectId, null, 0, 20));
        assertThrows(AccessDeniedException.class, () -> grades.listGroupGrades(outsider, projectId, 0, 20));
        login(teacher);
        assertThrows(ApiException.class, () -> grades.recordStudentGrade(teacher, projectId, studentGrade(outsider, 1)));
    }

    @Test
    void groupGradesNeedEvidenceWhenConfiguredAndMetricsRespectDefinitions() {
        login(teacher);
        assertThrows(ApiException.class, () -> grades.recordGroupGrade(teacher, projectId, teamGrade(2, null)));
        grades.recordGroupGrade(teacher, projectId, teamGrade(2, "demo"));
        assertThrows(ApiException.class, () -> metrics.record(teacher, projectId,
                new MetricRequest(rubricId, null, "hours", n(-1), null, null)));
        MetricResponse metric = metrics.record(teacher, projectId,
                new MetricRequest(rubricId, null, "hours", new BigDecimal("12.5"), null, null));
        assertEquals(1, metric.metricVersion());
        login(alice);
        assertEquals(1, grades.listGroupGrades(alice, projectId, 0, 20).totalElements());
        assertEquals(1, metrics.list(alice, projectId, 0, 20).totalElements());
    }

    @Test
    void otherRubricVersionRequiresExplicitTransition() {
        login(teacher);
        grades.recordStudentGrade(teacher, projectId, studentGrade(alice, 3));
        login(admin);
        UUID other = rubrics.publish(admin, rubricRequest(UUID.randomUUID().toString())).id();
        login(teacher);
        assertEquals(409, assertThrows(ApiException.class,
                () -> grades.recordStudentGrade(teacher, projectId, studentGrade(alice, other, 2, null))).getStatus().value());
        GradeResponse moved = grades.recordStudentGrade(teacher, projectId,
                studentGrade(alice, other, 2, new VersionTransition("Approved switch")));
        assertEquals(other, moved.rubricId());
        assertEquals(1, grades.listStudentGrades(teacher, projectId, null, 0, 1).content().size());
    }

    @Test
    void finalGradesUseConfiguredRuleOrRequireRationaleAndKeepHistory() {
        login(teacher);
        grades.recordStudentGrade(teacher, projectId, studentGrade(alice, 2));
        grades.recordGroupGrade(teacher, projectId, teamGrade(4, "x"));
        FinalGradesRequest noRationale = new FinalGradesRequest(rubricId, null, new Decision(null, n(2), null), null);
        assertThrows(ApiException.class, () -> finalGrades.record(teacher, projectId, noRationale));
        FinalGradesResponse result = finalGrades.record(teacher, projectId,
                new FinalGradesRequest(rubricId, null, null, List.of(byRule(alice, null))));
        assertEquals(0, new BigDecimal("2.5").compareTo(result.students().get(0).grade()));
        assertEquals(2, result.students().get(0).ruleVersion());
        assertThrows(ApiException.class, () -> finalGrades.record(teacher, projectId,
                new FinalGradesRequest(rubricId, null, null, List.of(byRule(alice, 1)))));
        finalGrades.record(teacher, projectId, new FinalGradesRequest(rubricId, null, null,
                List.of(new StudentDecision(alice.id(), null, n(3), "Considered all"))));
        login(alice);
        FinalGradeHistory own = finalGrades.getStudentFinalGrade(alice, projectId, alice.id());
        assertEquals("MANUAL", own.current().method());
        assertEquals(2, own.history().size());
        assertEquals("SUPERSEDED", own.history().get(1).status());
        assertThrows(AccessDeniedException.class, () -> finalGrades.getStudentFinalGrade(alice, projectId, bob.id()));
        assertThrows(Exception.class, () -> finalGrades.getProjectFinalGrade(alice, projectId));
        assertTrue(own.current().inputs() != null);
    }
}
