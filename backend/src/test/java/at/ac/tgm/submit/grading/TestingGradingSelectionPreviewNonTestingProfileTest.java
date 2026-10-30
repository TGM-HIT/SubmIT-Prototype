package at.ac.tgm.submit.grading;

import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecordRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gradingselectionpreviewprofile;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_GRADING_SELECTION_PREVIEW=true"
})
@ActiveProfiles("default")
class TestingGradingSelectionPreviewNonTestingProfileTest {
    @Autowired private StoredRecordRepository records;
    @Autowired private RecordStore store;

    @Test
    void optInPropertyDoesNotCreateAnyGradingPreviewRecordOutsideTestingProfile() {
        Set<String> projectIds = Set.of(
                TestingGradingSelectionPreviewConfiguration.TEACHER_PROJECT_ID.toString(),
                TestingGradingSelectionPreviewConfiguration.OTHER_TEACHER_PROJECT_ID.toString());
        Set<String> periodIds = Set.of(
                TestingGradingSelectionPreviewConfiguration.TEACHER_PERIOD_ID.toString(),
                TestingGradingSelectionPreviewConfiguration.OTHER_TEACHER_PERIOD_ID.toString());
        for (String type : Set.of("idea", "application_period", "allocation_run", "project_group",
                "confirmed_supervisor", "formal_application", "rubric_binding", "student_grade",
                "group_grade", "project_metric", "final_grade")) {
            assertTrue(records.findByType(type, PageRequest.of(0, 100)).getContent().stream()
                    .noneMatch(record -> projectIds.contains(record.getId().toString())
                            || contains(projectIds, record.getParentId())
                            || periodIds.contains(record.getId().toString())
                            || contains(periodIds, record.getParentId())
                            || contains(projectIds, store.parse(record).path("projectId").asText(null))),
                    "The grading preview is not allowed to create " + type + " records outside testing.");
        }
    }

    private boolean contains(Set<String> values, String value) {
        return value != null && values.contains(value);
    }
}
