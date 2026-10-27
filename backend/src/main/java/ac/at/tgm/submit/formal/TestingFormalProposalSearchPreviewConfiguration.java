package ac.at.tgm.submit.formal;

import com.fasterxml.jackson.databind.node.ObjectNode;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.data.StoredRecordRepository;
import ac.at.tgm.submit.user.UserAccount;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;

@Configuration
@Profile("testing")
@ConditionalOnProperty(name = "DADB_TESTING_FORMAL_PROPOSAL_SEARCH_PREVIEW", havingValue = "true")
public class TestingFormalProposalSearchPreviewConfiguration {
    public static final String PROPERTY = "DADB_TESTING_FORMAL_PROPOSAL_SEARCH_PREVIEW";
    public static final UUID TEACHER_PROJECT_ID = UUID.fromString("26a00000-0000-4000-8000-000000000001");
    public static final UUID OTHER_TEACHER_PROJECT_ID = UUID.fromString("26a00000-0000-4000-8000-000000000002");
    public static final String TEACHER_PROJECT_TITLE = "Formal Search Preview — River Lab";
    public static final String OTHER_TEACHER_PROJECT_TITLE = "Formal Search Preview — Wind Garden";

    @Bean
    @Order(4)
    ApplicationRunner seedFormalProposalSearchPreview(UserAccountRepository users, RecordStore store,
            StoredRecordRepository records) {
        return new FormalProposalSearchPreviewSeeder(users, store, records);
    }

    static final class FormalProposalSearchPreviewSeeder implements ApplicationRunner {
        private final UserAccountRepository users;
        private final RecordStore store;
        private final StoredRecordRepository records;

        FormalProposalSearchPreviewSeeder(UserAccountRepository users, RecordStore store,
                StoredRecordRepository records) {
            this.users = users;
            this.store = store;
            this.records = records;
        }

        @Override
        public void run(ApplicationArguments args) {
            seedProposal(TEACHER_PROJECT_ID, TEACHER_PROJECT_TITLE, "teacher01",
                    List.of("student04", "student05", "student06"),
                    new PreviewPeriod("River Lab Period", "2026/2027"));
            seedProposal(OTHER_TEACHER_PROJECT_ID, OTHER_TEACHER_PROJECT_TITLE, "teacher02",
                    List.of("student07", "student08", "student09"),
                    new PreviewPeriod("Wind Garden Period", "2027/2028"));
        }

        private void seedProposal(UUID projectId, String title, String teacherName, List<String> studentNames,
                PreviewPeriod period) {
            UserAccount admin = requireAccount("admin01");
            UserAccount teacher = requireAccount(teacherName);
            List<UserAccount> students = studentNames.stream().map(this::requireAccount).toList();
            ObjectNode project = store.object().put("title", title)
                    .put("description", "A fictional classroom project used only for local proposal search.")
                    .put("intendedOutcome", "A synthetic project report.")
                    .put("schoolYear", period.schoolYear());
            project.set("scopePoints", store.value(List.of("Use invented observations")));
            project.set("learningTopics", store.value(List.of("Scientific comparison")));
            StoredRecord existingProject = records.findById(projectId).orElse(null);
            if (existingProject == null) {
                records.save(new StoredRecord(projectId, new StoredRecord.Details("idea",
                        students.get(0).getId().toString(), null, "ACCEPTED_FOR_APPLICATIONS", null,
                        store.encode(project), Instant.now())));
            } else if (!"idea".equals(existingProject.getType())) {
                throw new IllegalStateException("Formal search preview project ID is occupied by another record.");
            }

            StoredRecord group = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                    "project_group", projectId.toString()).orElse(null);
            StoredRecord proposal = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                    "formal_application", projectId.toString()).orElse(null);
            if (group != null && proposal != null) {
                return;
            }
            if (group != null || proposal != null) {
                throw new IllegalStateException("Formal search preview project has incomplete seeded records.");
            }

            ObjectNode periodData = store.object().put("label", period.label()).put("schoolYear", period.schoolYear())
                    .put("opensAt", "2098-01-01T00:00:00Z").put("closesAt", "2099-01-01T00:00:00Z");
            periodData.set("projectIds", store.value(List.of(projectId)));
            StoredRecord periodRecord = store.create("application_period", admin.getId().toString(), null,
                    "FINALIZED", periodData);

            ObjectNode runData = store.object().put("initial", true).put("early", false);
            ObjectNode result = runData.putObject("result");
            var assignments = result.putArray("assignments");
            students.forEach(student -> assignments.addObject()
                    .put("studentId", student.getId().toString()).put("projectId", projectId.toString()));
            result.putArray("unassignedStudents");
            result.putArray("unassignedProjects");
            StoredRecord run = store.create("allocation_run", admin.getId().toString(), periodRecord.getId().toString(),
                    "FINALIZED", runData);

            ObjectNode groupData = store.object().put("periodId", periodRecord.getId().toString())
                    .put("allocationRunId", run.getId().toString()).put("projectId", projectId.toString());
            groupData.set("studentIds", store.value(students.stream().map(student -> student.getId().toString()).toList()));
            store.create("project_group", teacher.getId().toString(), projectId.toString(), "ALLOCATED", groupData);

            ObjectNode assignmentData = store.object().put("teacherId", teacher.getId().toString());
            store.create("confirmed_supervisor", admin.getId().toString(), projectId.toString(),
                    "CONFIRMED", assignmentData);
            store.create("formal_application", students.get(0).getId().toString(), projectId.toString(),
                    "SUBMITTED", proposalPayload(students));
        }

        private ObjectNode proposalPayload(List<UserAccount> students) {
            ObjectNode proposal = store.object().put("topic", "Synthetic formal search preview proposal")
                    .put("initialSituation", "A fictional project needs a concise plan.")
                    .put("objective", "Compare generated observations.")
                    .put("cooperationPartner", "");
            proposal.set("individualTopics", store.value(students.stream()
                    .map(student -> new PreviewTopic(student.getId().toString(), "Synthetic topic")).toList()));
            proposal.set("investigationConcerns", store.value(students.stream()
                    .map(student -> new PreviewText(student.getId().toString(), "Fictional investigation question."))
                    .toList()));
            proposal.set("plannedResults", store.value(students.stream()
                    .map(student -> new PreviewText(student.getId().toString(), "Synthetic comparison table.")).toList()));
            proposal.set("milestones", store.value(List.of(new PreviewMilestone("Review generated observations",
                    "2027-03-01"))));
            return proposal;
        }

        private UserAccount requireAccount(String username) {
            return users.findByUsername(username).orElseThrow(() ->
                    new IllegalStateException("Formal search preview requires seeded account " + username + "."));
        }

        private record PreviewTopic(String studentId, String topic) {
        }

        private record PreviewText(String studentId, String text) {
        }

        private record PreviewMilestone(String text, String date) {
        }

        private record PreviewPeriod(String label, String schoolYear) {
        }
    }
}
