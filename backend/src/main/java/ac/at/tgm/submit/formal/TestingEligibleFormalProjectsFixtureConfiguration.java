package ac.at.tgm.submit.formal;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.data.StoredRecordRepository;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

/** Isolated opt-in finalized Formal proposal choices for local UI verification. */
@Configuration
@Profile("testing")
@ConditionalOnProperty(name = "dadb.testing.eligible-formal-project-preview", havingValue = "true")
public class TestingEligibleFormalProjectsFixtureConfiguration {
    public static final UUID EXISTING_PROJECT_ID = UUID.fromString("22a00000-0000-4000-8000-000000000001");
    public static final UUID NEW_PROJECT_ID = UUID.fromString("22a00000-0000-4000-8000-000000000002");
    public static final UUID PRELIMINARY_PROJECT_ID = UUID.fromString("22a00000-0000-4000-8000-000000000003");
    public static final UUID EXISTING_PERIOD_ID = UUID.fromString("22b00000-0000-4000-8000-000000000001");
    public static final UUID NEW_PERIOD_ID = UUID.fromString("22b00000-0000-4000-8000-000000000002");
    public static final UUID PRELIMINARY_PERIOD_ID = UUID.fromString("22b00000-0000-4000-8000-000000000003");
    public static final UUID EXISTING_RUN_ID = UUID.fromString("22c00000-0000-4000-8000-000000000001");
    public static final UUID NEW_RUN_ID = UUID.fromString("22c00000-0000-4000-8000-000000000002");
    public static final UUID PRELIMINARY_RUN_ID = UUID.fromString("22c00000-0000-4000-8000-000000000003");
    private static final String FINALIZED = "FINALIZED";
    private static final String ALLOCATED = "ALLOCATED";
    private static final String COMPLETED = "COMPLETED";
    private static final int FIXTURE_YEAR = 2027;
    private static final List<String> STUDENT_USERNAMES = List.of("student01", "student02", "student03");

    @Bean
    @Order(2)
    ApplicationRunner seedTestingEligibleFormalProjects(UserAccountRepository users,
            RecordStore store, StoredRecordRepository records) {
        return args -> seed(users, store, records);
    }

    private void seed(UserAccountRepository users, RecordStore store, StoredRecordRepository records) {
        List<UserAccount> students = STUDENT_USERNAMES.stream()
                .map(username -> users.findByUsername(username)
                        .orElseThrow(() -> new IllegalStateException(
                                "Eligible Formal preview requires the seeded " + username + " account.")))
                .toList();
        String teacherId = users.findByUsername("teacher01").orElseThrow().getId().toString();
        String adminId = users.findByUsername("admin01").orElseThrow().getId().toString();
        List<String> studentIds = students.stream().map(student -> student.getId().toString()).toList();

        ensure(records, store, project(store, EXISTING_PROJECT_ID, "Existing proposal",
                "2026/2027", teacherId));
        ensure(records, store, project(store, NEW_PROJECT_ID, "New proposal", "2027/2028", teacherId));
        ensure(records, store, project(store, PRELIMINARY_PROJECT_ID, "Preliminary-only project",
                "2028/2029", teacherId));

        ensure(records, store, period(store, new PeriodSpec(EXISTING_PERIOD_ID,
                "2026/2027", "2026/2027", EXISTING_PROJECT_ID, FINALIZED, adminId)));
        ensure(records, store, period(store, new PeriodSpec(NEW_PERIOD_ID,
                "2027/2028", "2027/2028", NEW_PROJECT_ID, FINALIZED, adminId)));
        ensure(records, store, period(store, new PeriodSpec(PRELIMINARY_PERIOD_ID,
                "Preliminary-only 2028/2029", "2028/2029", PRELIMINARY_PROJECT_ID, ALLOCATED,
                adminId)));

        ensure(records, store, run(store, new RunSpec(EXISTING_RUN_ID, EXISTING_PERIOD_ID,
                EXISTING_PROJECT_ID, studentIds, false, FINALIZED, adminId)));
        ensure(records, store, run(store, new RunSpec(NEW_RUN_ID, NEW_PERIOD_ID,
                NEW_PROJECT_ID, studentIds, false, FINALIZED, adminId)));
        ensure(records, store, run(store, new RunSpec(PRELIMINARY_RUN_ID, PRELIMINARY_PERIOD_ID,
                PRELIMINARY_PROJECT_ID, studentIds, true, COMPLETED, adminId)));

        ensure(records, store, group(store, new GroupSpec(EXISTING_PROJECT_ID, EXISTING_PERIOD_ID,
                EXISTING_RUN_ID, studentIds, adminId)));
        ensure(records, store, group(store, new GroupSpec(NEW_PROJECT_ID, NEW_PERIOD_ID,
                NEW_RUN_ID, studentIds, adminId)));
        ensure(records, store, formalProposal(store, students));
    }

    private FixtureRecord project(RecordStore store, UUID id, String title, String schoolYear, String ownerId) {
        ObjectNode payload = store.object().put("title", title).put("schoolYear", schoolYear)
                .put("description", "Synthetic local preview project.")
                .put("intendedOutcome", "Showcase the project selection flow.");
        return new FixtureRecord(id, "idea", ownerId, null, "ACCEPTED_FOR_APPLICATIONS", payload);
    }

    private FixtureRecord period(RecordStore store, PeriodSpec period) {
        ObjectNode payload = store.object().put("label", period.label()).put("schoolYear", period.schoolYear())
                .put("opensAt", "2098-01-01T00:00:00Z").put("closesAt", "2099-01-01T00:00:00Z");
        payload.set("projectIds", store.value(List.of(period.projectId().toString())));
        return new FixtureRecord(period.id(), "application_period", period.ownerId(), null, period.status(), payload);
    }

    private FixtureRecord run(RecordStore store, RunSpec run) {
        ObjectNode payload = store.object().put("initial", !run.early()).put("early", run.early());
        ObjectNode result = payload.putObject("result");
        var assignments = result.putArray("assignments");
        run.studentIds().forEach(studentId -> assignments.addObject()
                .put("studentId", studentId).put("projectId", run.projectId().toString()));
        result.putArray("unassignedStudents");
        result.putArray("unassignedProjects");
        return new FixtureRecord(run.id(), "allocation_run", run.ownerId(), run.periodId().toString(),
                run.status(), payload);
    }

    private FixtureRecord group(RecordStore store, GroupSpec group) {
        ObjectNode payload = store.object().put("projectId", group.projectId().toString())
                .put("periodId", group.periodId().toString()).put("allocationRunId", group.runId().toString());
        payload.set("studentIds", store.value(group.studentIds()));
        return new FixtureRecord(null, "project_group", group.ownerId(), group.projectId().toString(),
                ALLOCATED, payload);
    }

    private FixtureRecord formalProposal(RecordStore store, List<UserAccount> students) {
        ObjectNode payload = store.object().put("topic", "Synthetic garden water study")
                .put("initialSituation", "A model garden needs a clearer plan.")
                .put("objective", "Compare synthetic watering plans.").put("cooperationPartner", "");
        payload.set("individualTopics", store.value(List.of(
                new FixtureTopic(students.get(0).getId().toString(), "Soil observations"),
                new FixtureTopic(students.get(1).getId().toString(), "Interval comparison"),
                new FixtureTopic(students.get(2).getId().toString(), "Results display"))));
        payload.set("investigationConcerns", store.value(List.of(
                new FixtureText(students.get(0).getId().toString(), "How does model soil retain water?"),
                new FixtureText(students.get(1).getId().toString(), "Which interval uses less water?"),
                new FixtureText(students.get(2).getId().toString(), "How can results be compared?"))));
        payload.set("plannedResults", store.value(List.of(
                new FixtureText(students.get(0).getId().toString(), "A synthetic readings table."),
                new FixtureText(students.get(1).getId().toString(), "A generated measurement comparison."),
                new FixtureText(students.get(2).getId().toString(), "A paper display prototype."))));
        payload.set("milestones", store.value(List.of(
                new FixtureMilestone("Review generated measurements", LocalDate.of(FIXTURE_YEAR, 3, 1)))));
        return new FixtureRecord(null, "formal_application", students.get(0).getId().toString(),
                EXISTING_PROJECT_ID.toString(), "DRAFT", payload);
    }

    private void ensure(StoredRecordRepository records, RecordStore store, FixtureRecord fixture) {
        if (fixture.id() == null) {
            ensureGeneratedRecord(records, store, fixture);
            return;
        }
        ensureFixedRecord(records, store, fixture);
    }

    private void ensureFixedRecord(StoredRecordRepository records, RecordStore store, FixtureRecord fixture) {
        StoredRecord existing = records.findById(fixture.id()).orElse(null);
        if (existing == null) {
            records.save(new StoredRecord(fixture.id(), new StoredRecord.Details(fixture.type(),
                    fixture.ownerId(), fixture.parentId(), fixture.status(), null,
                    store.encode(fixture.payload()), Instant.now())));
            return;
        }
        if (!fixture.type().equals(existing.getType()) || !fixture.status().equals(existing.getStatus())
                || !java.util.Objects.equals(fixture.parentId(), existing.getParentId())
                || !fixture.payload().equals(store.parse(existing))) {
            throw new IllegalStateException("Existing eligible Formal preview fixture differs: " + fixture.id());
        }
    }

    private void ensureGeneratedRecord(StoredRecordRepository records, RecordStore store, FixtureRecord fixture) {
        StoredRecord existing = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                fixture.type(), fixture.parentId()).orElse(null);
        if (existing == null) {
            store.create(fixture.type(), fixture.ownerId(), fixture.parentId(), fixture.status(), fixture.payload());
        } else if (!fixture.status().equals(existing.getStatus())
                || !fixture.payload().equals(store.parse(existing))) {
            throw new IllegalStateException("Existing eligible Formal preview fixture differs: " + fixture.type());
        }
    }

    private record PeriodSpec(UUID id, String label, String schoolYear, UUID projectId, String status,
            String ownerId) {
    }

    private record RunSpec(UUID id, UUID periodId, UUID projectId, List<String> studentIds,
            boolean early, String status, String ownerId) {
    }

    private record GroupSpec(UUID projectId, UUID periodId, UUID runId, List<String> studentIds, String ownerId) {
    }

    private record FixtureRecord(UUID id, String type, String ownerId, String parentId, String status,
            ObjectNode payload) {
    }

    private record FixtureTopic(String studentId, String topic) {
    }

    private record FixtureText(String studentId, String text) {
    }

    private record FixtureMilestone(String text, LocalDate date) {
    }
}
