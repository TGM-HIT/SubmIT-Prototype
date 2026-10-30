package at.ac.tgm.submit.grading;

import static at.ac.tgm.submit.grading.GradingKeys.ACTIVE;
import static at.ac.tgm.submit.grading.GradingKeys.ASSESSED_ON;
import static at.ac.tgm.submit.grading.GradingKeys.BINDING;
import static at.ac.tgm.submit.grading.GradingKeys.CATEGORY_ID;
import static at.ac.tgm.submit.grading.GradingKeys.COMMENTS;
import static at.ac.tgm.submit.grading.GradingKeys.CRITERION_ID;
import static at.ac.tgm.submit.grading.GradingKeys.CURRENT;
import static at.ac.tgm.submit.grading.GradingKeys.DECIDED_AT;
import static at.ac.tgm.submit.grading.GradingKeys.DECIDED_BY;
import static at.ac.tgm.submit.grading.GradingKeys.EVIDENCE;
import static at.ac.tgm.submit.grading.GradingKeys.FINAL_GRADE;
import static at.ac.tgm.submit.grading.GradingKeys.GRADE;
import static at.ac.tgm.submit.grading.GradingKeys.GROUP_GRADE;
import static at.ac.tgm.submit.grading.GradingKeys.INPUTS;
import static at.ac.tgm.submit.grading.GradingKeys.MEASURED_AT;
import static at.ac.tgm.submit.grading.GradingKeys.METRIC;
import static at.ac.tgm.submit.grading.GradingKeys.METRIC_ID;
import static at.ac.tgm.submit.grading.GradingKeys.METRIC_VERSION;
import static at.ac.tgm.submit.grading.GradingKeys.NOTE;
import static at.ac.tgm.submit.grading.GradingKeys.PROJECT_OWNER;
import static at.ac.tgm.submit.grading.GradingKeys.RUBRIC;
import static at.ac.tgm.submit.grading.GradingKeys.RUBRIC_ID;
import static at.ac.tgm.submit.grading.GradingKeys.RUBRIC_VERSION;
import static at.ac.tgm.submit.grading.GradingKeys.SCOPE;
import static at.ac.tgm.submit.grading.GradingKeys.SCOPE_PROJECT;
import static at.ac.tgm.submit.grading.GradingKeys.STUDENT_GRADE;
import static at.ac.tgm.submit.grading.GradingKeys.STUDENT_ID;
import static at.ac.tgm.submit.grading.GradingKeys.SUBJECT_STUDENT;
import static at.ac.tgm.submit.grading.GradingKeys.TEACHER_ID;
import static at.ac.tgm.submit.grading.GradingKeys.VALUE;
import static at.ac.tgm.submit.grading.GradingKeys.VERSION;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.data.StoredRecordRepository;
import at.ac.tgm.submit.user.UserAccount;
import at.ac.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

/** Reproducible, opt-in grading selector records for the local testing preview only. */
@Configuration
@Profile("testing & !testing-tgm")
@ConditionalOnProperty(name = TestingGradingSelectionPreviewConfiguration.PROPERTY, havingValue = "true")
public class TestingGradingSelectionPreviewConfiguration {
    public static final String PROPERTY = "DADB_TESTING_GRADING_SELECTION_PREVIEW";
    public static final UUID TEACHER_PROJECT_ID = UUID.fromString("27a00000-0000-4000-8000-000000000001");
    public static final UUID OTHER_TEACHER_PROJECT_ID = UUID.fromString("27a00000-0000-4000-8000-000000000002");
    public static final UUID TEACHER_PERIOD_ID = UUID.fromString("27b00000-0000-4000-8000-000000000001");
    public static final UUID OTHER_TEACHER_PERIOD_ID = UUID.fromString("27b00000-0000-4000-8000-000000000002");
    public static final String TEACHER_PROJECT_TITLE = "Grading Selection Preview — River Lab";
    public static final String OTHER_TEACHER_PROJECT_TITLE = "Grading Selection Preview — Wind Garden";
    public static final String TEACHER_PERIOD_LABEL = "River Lab Finalized Period";
    public static final String OTHER_TEACHER_PERIOD_LABEL = "Wind Garden Finalized Period";
    private static final UUID TEACHER_RUN_ID = UUID.fromString("27c00000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TEACHER_RUN_ID = UUID.fromString("27c00000-0000-4000-8000-000000000002");
    private static final UUID TEACHER_PROPOSAL_ID = UUID.fromString("27d00000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TEACHER_PROPOSAL_ID = UUID.fromString("27d00000-0000-4000-8000-000000000002");
    private static final UUID TEACHER_GROUP_ID = UUID.fromString("27e00000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TEACHER_GROUP_ID = UUID.fromString("27e00000-0000-4000-8000-000000000002");
    private static final UUID TEACHER_SUPERVISOR_ID = UUID.fromString("27f00000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TEACHER_SUPERVISOR_ID = UUID.fromString("27f00000-0000-4000-8000-000000000002");
    private static final UUID TEACHER_BINDING_ID = UUID.fromString("27f10000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TEACHER_BINDING_ID = UUID.fromString("27f10000-0000-4000-8000-000000000002");
    private static final UUID TEACHER_INDIVIDUAL_GRADE_ID = UUID.fromString("27f20000-0000-4000-8000-000000000001");
    private static final UUID TEACHER_SENTINEL_GRADE_ID = UUID.fromString("27f20000-0000-4000-8000-000000000002");
    private static final UUID OTHER_TEACHER_INDIVIDUAL_GRADE_ID =
            UUID.fromString("27f20000-0000-4000-8000-000000000003");
    private static final UUID TEACHER_GROUP_GRADE_ID = UUID.fromString("27f30000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TEACHER_GROUP_GRADE_ID =
            UUID.fromString("27f30000-0000-4000-8000-000000000002");
    private static final UUID TEACHER_METRIC_ID = UUID.fromString("27f40000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TEACHER_METRIC_ID = UUID.fromString("27f40000-0000-4000-8000-000000000002");
    private static final UUID TEACHER_PROJECT_FINAL_ID = UUID.fromString("27f50000-0000-4000-8000-000000000001");
    private static final UUID TEACHER_STUDENT_FINAL_ID = UUID.fromString("27f50000-0000-4000-8000-000000000002");
    private static final UUID OTHER_TEACHER_PROJECT_FINAL_ID =
            UUID.fromString("27f50000-0000-4000-8000-000000000003");
    private static final UUID OTHER_TEACHER_STUDENT_FINAL_ID =
            UUID.fromString("27f50000-0000-4000-8000-000000000004");
    private static final String TESTING_RUBRIC_VERSION = "dev55-draft-testing-v1";
    private static final String TEST_CRITERION_ID = "a1-01";
    private static final String TEST_METRIC_ID = "testing-supplemental-observation";
    private static final String FIXTURE_TIMESTAMP = "2026-10-27T12:00:00Z";
    private static final String FIXTURE_DATE = "2026-10-26";
    private static final int RIVER_INDIVIDUAL_GRADE = 4;
    private static final int RIVER_SENTINEL_GRADE = 2;
    private static final int WIND_INDIVIDUAL_GRADE = 5;
    private static final int RIVER_GROUP_GRADE = 4;
    private static final int WIND_GROUP_GRADE = 3;
    private static final int RIVER_METRIC_VALUE = 12;
    private static final int WIND_METRIC_VALUE = 7;
    private static final int RIVER_PROJECT_FINAL_GRADE = 4;
    private static final int RIVER_STUDENT_FINAL_GRADE = 4;
    private static final int WIND_PROJECT_FINAL_GRADE = 3;
    private static final int WIND_STUDENT_FINAL_GRADE = 5;
    private static final int PREVIEW_METRIC_VERSION = 1;
    private static final int FIXTURE_MILESTONE_YEAR = 2027;

    @Bean
    @Order(5)
    ApplicationRunner seedGradingSelectionPreview(UserAccountRepository users, RecordStore store,
            StoredRecordRepository records) {
        return new GradingSelectionPreviewSeeder(users, store, records);
    }

    static final class GradingSelectionPreviewSeeder implements ApplicationRunner {
        private final UserAccountRepository users;
        private final RecordStore store;
        private final StoredRecordRepository records;

        GradingSelectionPreviewSeeder(UserAccountRepository users, RecordStore store,
                StoredRecordRepository records) {
            this.users = users;
            this.store = store;
            this.records = records;
        }

        @Override
        public void run(ApplicationArguments args) {
            String adminId = account("admin01").getId().toString();
            UserAccount teacherOne = account("teacher01");
            UserAccount teacherTwo = account("teacher02");
            UserAccount studentOne = account("student01");
            UserAccount studentTwo = account("student02");
            UserAccount studentThree = account("student03");
            UserAccount studentFour = account("student04");
            UserAccount studentFive = account("student05");
            StoredRecord rubric = rubric();
            String rubricId = rubric.getId().toString();
            String rubricVersion = store.parse(rubric).path(VERSION).asText();
            if (!TESTING_RUBRIC_VERSION.equals(rubricVersion)) {
                throw new IllegalStateException("Grading selection preview requires the versioned testing rubric.");
            }

            seedProject(TEACHER_PROJECT_ID, TEACHER_PROJECT_TITLE, "2026/2027", studentTwo);
            seedProject(OTHER_TEACHER_PROJECT_ID, OTHER_TEACHER_PROJECT_TITLE, "2027/2028", studentOne);
            seedPeriod(new PeriodSeed(TEACHER_PERIOD_ID, TEACHER_PERIOD_LABEL, "2026/2027",
                    TEACHER_PROJECT_ID, adminId));
            seedPeriod(new PeriodSeed(OTHER_TEACHER_PERIOD_ID, OTHER_TEACHER_PERIOD_LABEL, "2027/2028",
                    OTHER_TEACHER_PROJECT_ID, adminId));
            seedRun(TEACHER_RUN_ID, TEACHER_PERIOD_ID, TEACHER_PROJECT_ID,
                    List.of(studentTwo, studentOne, studentThree), adminId);
            seedRun(OTHER_TEACHER_RUN_ID, OTHER_TEACHER_PERIOD_ID, OTHER_TEACHER_PROJECT_ID,
                    List.of(studentOne, studentFour, studentFive), adminId);
            seedGroup(new GroupSeed(TEACHER_GROUP_ID, TEACHER_PROJECT_ID, TEACHER_PERIOD_ID, TEACHER_RUN_ID,
                    List.of(studentTwo, studentOne, studentThree), adminId));
            seedGroup(new GroupSeed(OTHER_TEACHER_GROUP_ID, OTHER_TEACHER_PROJECT_ID, OTHER_TEACHER_PERIOD_ID,
                    OTHER_TEACHER_RUN_ID, List.of(studentOne, studentFour, studentFive), adminId));
            seedSupervisor(TEACHER_SUPERVISOR_ID, TEACHER_PROJECT_ID, teacherOne, adminId);
            seedSupervisor(OTHER_TEACHER_SUPERVISOR_ID, OTHER_TEACHER_PROJECT_ID, teacherTwo, adminId);
            seedProposal(TEACHER_PROPOSAL_ID, TEACHER_PROJECT_ID,
                    List.of(studentTwo, studentOne, studentThree));
            seedProposal(OTHER_TEACHER_PROPOSAL_ID, OTHER_TEACHER_PROJECT_ID,
                    List.of(studentOne, studentFour, studentFive));
            seedBinding(TEACHER_BINDING_ID, TEACHER_PROJECT_ID, rubricId, teacherOne);
            seedBinding(OTHER_TEACHER_BINDING_ID, OTHER_TEACHER_PROJECT_ID, rubricId, teacherTwo);

            seedAssessment(new AssessmentSeed(TEACHER_INDIVIDUAL_GRADE_ID, STUDENT_GRADE, TEACHER_PROJECT_ID,
                    studentOne, rubricId, rubricVersion, teacherOne, "Individual analysis",
                    RIVER_INDIVIDUAL_GRADE, "student01's River Lab assessment."));
            seedAssessment(new AssessmentSeed(TEACHER_SENTINEL_GRADE_ID, STUDENT_GRADE, TEACHER_PROJECT_ID,
                    studentTwo, rubricId, rubricVersion, teacherOne, "Private sentinel", RIVER_SENTINEL_GRADE,
                    "OTHER_STUDENT_GRADING_SENTINEL"));
            seedAssessment(new AssessmentSeed(OTHER_TEACHER_INDIVIDUAL_GRADE_ID, STUDENT_GRADE,
                    OTHER_TEACHER_PROJECT_ID, studentOne, rubricId, rubricVersion, teacherTwo,
                    "Individual comparison", WIND_INDIVIDUAL_GRADE, "student01's Wind Garden assessment."));
            seedAssessment(new AssessmentSeed(TEACHER_GROUP_GRADE_ID, GROUP_GRADE, TEACHER_PROJECT_ID,
                    null, rubricId, rubricVersion, teacherOne, "Group collaboration", RIVER_GROUP_GRADE,
                    "River Lab group history."));
            seedAssessment(new AssessmentSeed(OTHER_TEACHER_GROUP_GRADE_ID, GROUP_GRADE, OTHER_TEACHER_PROJECT_ID,
                    null, rubricId, rubricVersion, teacherTwo, "Group delivery", WIND_GROUP_GRADE,
                    "Wind Garden group history."));
            seedMetric(new MetricSeed(TEACHER_METRIC_ID, TEACHER_PROJECT_ID, rubricId, rubricVersion, teacherOne,
                    "River Lab metric observation", RIVER_METRIC_VALUE));
            seedMetric(new MetricSeed(OTHER_TEACHER_METRIC_ID, OTHER_TEACHER_PROJECT_ID, rubricId, rubricVersion,
                    teacherTwo, "Wind Garden metric observation", WIND_METRIC_VALUE));
            seedFinalGrade(new FinalGradeSeed(TEACHER_PROJECT_FINAL_ID, TEACHER_PROJECT_ID, PROJECT_OWNER, null,
                    rubricId, rubricVersion, teacherOne, RIVER_PROJECT_FINAL_GRADE, "River Lab project final grade."));
            seedFinalGrade(new FinalGradeSeed(TEACHER_STUDENT_FINAL_ID, TEACHER_PROJECT_ID,
                    studentOne.getId().toString(), studentOne.getId().toString(), rubricId, rubricVersion, teacherOne,
                    RIVER_STUDENT_FINAL_GRADE, "River Lab student final grade."));
            seedFinalGrade(new FinalGradeSeed(OTHER_TEACHER_PROJECT_FINAL_ID, OTHER_TEACHER_PROJECT_ID, PROJECT_OWNER,
                    null, rubricId, rubricVersion, teacherTwo, WIND_PROJECT_FINAL_GRADE,
                    "Wind Garden project final grade."));
            seedFinalGrade(new FinalGradeSeed(OTHER_TEACHER_STUDENT_FINAL_ID, OTHER_TEACHER_PROJECT_ID,
                    studentOne.getId().toString(), studentOne.getId().toString(), rubricId, rubricVersion, teacherTwo,
                    WIND_STUDENT_FINAL_GRADE, "Wind Garden student final grade."));
        }

        private void seedProject(UUID id, String title, String schoolYear, UserAccount owner) {
            ObjectNode payload = store.object().put("title", title).put("schoolYear", schoolYear)
                    .put("description", "Synthetic project for the local grading selection preview.")
                    .put("intendedOutcome", "A distinct testing-only grading walkthrough.");
            ensure(new PreviewRecord(id, "idea", owner.getId().toString(), null, "ACCEPTED_FOR_APPLICATIONS", payload));
        }

        private void seedPeriod(PeriodSeed seed) {
            ObjectNode payload = store.object().put("label", seed.label()).put("schoolYear", seed.schoolYear())
                    .put("opensAt", "2098-01-01T00:00:00Z").put("closesAt", "2099-01-01T00:00:00Z");
            payload.set("projectIds", store.value(List.of(seed.projectId().toString())));
            ensure(new PreviewRecord(seed.id(), "application_period", seed.adminId(), null, "FINALIZED", payload));
        }

        private void seedRun(UUID id, UUID periodId, UUID projectId, List<UserAccount> students, String adminId) {
            ObjectNode payload = store.object().put("initial", true).put("early", false);
            ObjectNode result = payload.putObject("result");
            var assignments = result.putArray("assignments");
            students.forEach(student -> assignments.addObject()
                    .put("studentId", student.getId().toString()).put("projectId", projectId.toString()));
            result.putArray("unassignedStudents");
            result.putArray("unassignedProjects");
            ensure(new PreviewRecord(id, "allocation_run", adminId, periodId.toString(), "FINALIZED", payload));
        }

        private void seedGroup(GroupSeed seed) {
            ObjectNode payload = store.object().put("projectId", seed.projectId().toString())
                    .put("periodId", seed.periodId().toString()).put("allocationRunId", seed.runId().toString());
            payload.set("studentIds", store.value(seed.students().stream()
                    .map(student -> student.getId().toString()).toList()));
            ensure(new PreviewRecord(seed.id(), "project_group", seed.adminId(), seed.projectId().toString(),
                    "ALLOCATED", payload));
        }

        private void seedSupervisor(UUID id, UUID projectId, UserAccount teacher, String adminId) {
            ObjectNode payload = store.object().put("teacherId", teacher.getId().toString());
            ensure(new PreviewRecord(id, "confirmed_supervisor", adminId, projectId.toString(), "CONFIRMED", payload));
        }

        private void seedProposal(UUID id, UUID projectId, List<UserAccount> students) {
            ObjectNode payload = store.object().put("topic", "Synthetic grading selection proposal")
                    .put("initialSituation", "A fictional project needs a concise plan.")
                    .put("objective", "Compare generated project outcomes.").put("cooperationPartner", "");
            payload.set("individualTopics", store.value(students.stream()
                    .map(student -> new PreviewTopic(student.getId().toString(), "Synthetic individual topic"))
                    .toList()));
            payload.set("investigationConcerns", store.value(students.stream()
                    .map(student -> new PreviewText(student.getId().toString(), "Synthetic investigation concern"))
                    .toList()));
            payload.set("plannedResults", store.value(students.stream()
                    .map(student -> new PreviewText(student.getId().toString(), "Synthetic planned result")).toList()));
            payload.set("milestones", store.value(List.of(new PreviewMilestone("Review generated observations",
                    LocalDate.of(FIXTURE_MILESTONE_YEAR, 3, 1)))));
            ensure(new PreviewRecord(id, "formal_application", students.get(0).getId().toString(),
                    projectId.toString(), "APPROVED", payload));
        }

        private void seedBinding(UUID id, UUID projectId, String rubricId, UserAccount teacher) {
            ObjectNode payload = store.object().put(RUBRIC_ID, rubricId).put("reason", "Initial binding")
                    .put("recordedBy", teacher.getId().toString()).put("recordedAt", FIXTURE_TIMESTAMP);
            ensure(new PreviewRecord(id, BINDING, teacher.getId().toString(), projectId.toString(), "BOUND", payload));
        }

        private void seedAssessment(AssessmentSeed seed) {
            ObjectNode payload = store.object().put(RUBRIC_ID, seed.rubricId()).put(RUBRIC_VERSION, seed.rubricVersion())
                    .put(CRITERION_ID, TEST_CRITERION_ID).put(CATEGORY_ID, categoryId(rubric(), TEST_CRITERION_ID))
                    .put(GRADE, seed.grade()).put(ASSESSED_ON, FIXTURE_DATE)
                    .put(EVIDENCE, seed.criterion()).put(COMMENTS, seed.note())
                    .put(TEACHER_ID, seed.teacher().getId().toString())
                    .put("recordedAt", FIXTURE_TIMESTAMP);
            String owner = seed.student() == null ? null : seed.student().getId().toString();
            if (seed.student() != null) {
                payload.put(STUDENT_ID, owner);
            }
            ensure(new PreviewRecord(seed.id(), seed.type(), owner, seed.projectId().toString(), ACTIVE, payload));
        }

        private void seedMetric(MetricSeed seed) {
            ObjectNode payload = store.object().put(RUBRIC_ID, seed.rubricId())
                    .put(RUBRIC_VERSION, seed.rubricVersion())
                    .put(METRIC_ID, TEST_METRIC_ID).put(METRIC_VERSION, PREVIEW_METRIC_VERSION)
                    .put(VALUE, seed.value()).put(MEASURED_AT, FIXTURE_TIMESTAMP)
                    .put("recordedBy", seed.teacher().getId().toString())
                    .put("recordedAt", FIXTURE_TIMESTAMP).put(NOTE, seed.note());
            ensure(new PreviewRecord(seed.id(), METRIC, seed.teacher().getId().toString(),
                    seed.projectId().toString(), ACTIVE, payload));
        }

        private void seedFinalGrade(FinalGradeSeed seed) {
            ObjectNode payload = store.object().put(RUBRIC_ID, seed.rubricId()).put(RUBRIC_VERSION, seed.rubricVersion())
                    .put(SCOPE, seed.studentId() == null ? SCOPE_PROJECT : SUBJECT_STUDENT)
                    .put(GRADE, seed.grade()).put("method", "MANUAL").put("rationale", seed.rationale())
                    .put(DECIDED_BY, seed.teacher().getId().toString()).put(DECIDED_AT, FIXTURE_TIMESTAMP);
            if (seed.studentId() != null) {
                payload.put(STUDENT_ID, seed.studentId());
            }
            payload.set(INPUTS, store.object().putArray("studentGrades").addObject()
                    .put(CRITERION_ID, TEST_CRITERION_ID).put(GRADE, seed.grade())
                    .put(RUBRIC_VERSION, seed.rubricVersion()));
            ensure(new PreviewRecord(seed.id(), FINAL_GRADE, seed.owner(), seed.projectId().toString(), CURRENT, payload));
        }

        private StoredRecord rubric() {
            return store.page(RUBRIC, null, null, org.springframework.data.domain.Pageable.unpaged()).getContent()
                    .stream().filter(record -> TESTING_RUBRIC_VERSION.equals(
                            store.parse(record).path(VERSION).asText()))
                    .findFirst().orElseThrow(() -> new IllegalStateException(
                            "Grading selection preview requires the testing rubric fixture."));
        }

        private String categoryId(StoredRecord rubric, String criterionId) {
            for (JsonNode category : store.parse(rubric).path("categories")) {
                for (JsonNode criterion : category.path("criteria")) {
                    if (criterionId.equals(criterion.path("id").asText())) {
                        return category.path("id").asText();
                    }
                }
            }
            throw new IllegalStateException("Testing rubric is missing grading selection criterion " + criterionId);
        }

        private UserAccount account(String username) {
            return users.findByUsername(username).orElseThrow(() ->
                    new IllegalStateException("Grading selection preview requires seeded account " + username + "."));
        }

        private void ensure(PreviewRecord record) {
            StoredRecord existing = records.findById(record.id()).orElse(null);
            if (existing == null) {
                records.save(new StoredRecord(record.id(), new StoredRecord.Details(record.type(), record.ownerId(),
                        record.parentId(), record.status(), null, store.encode(record.payload()), Instant.now())));
                return;
            }
            if (!record.type().equals(existing.getType()) || !record.status().equals(existing.getStatus())
                    || !java.util.Objects.equals(record.ownerId(), existing.getOwnerId())
                    || !java.util.Objects.equals(record.parentId(), existing.getParentId())
                    || !record.payload().equals(store.parse(existing))) {
                throw new IllegalStateException("Existing grading selection preview record conflicts: " + record.id());
            }
        }

        private record PeriodSeed(UUID id, String label, String schoolYear, UUID projectId, String adminId) { }

        private record GroupSeed(UUID id, UUID projectId, UUID periodId, UUID runId, List<UserAccount> students,
                String adminId) { }

        private record AssessmentSeed(UUID id, String type, UUID projectId, UserAccount student, String rubricId,
                String rubricVersion, UserAccount teacher, String criterion, int grade, String note) { }

        private record MetricSeed(UUID id, UUID projectId, String rubricId, String rubricVersion,
                UserAccount teacher, String note, int value) { }

        private record FinalGradeSeed(UUID id, UUID projectId, String owner, String studentId, String rubricId,
                String rubricVersion, UserAccount teacher, int grade, String rationale) { }

        private record PreviewRecord(UUID id, String type, String ownerId, String parentId, String status,
                ObjectNode payload) { }

        private record PreviewTopic(String studentId, String topic) { }

        private record PreviewText(String studentId, String text) { }

        private record PreviewMilestone(String text, LocalDate date) { }
    }
}
