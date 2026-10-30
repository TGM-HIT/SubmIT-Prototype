package at.ac.tgm.submit.grading;

import static org.junit.jupiter.api.Assertions.assertTrue;

import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:gradingselectionpreviewdisabled;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_GRADING_SELECTION_PREVIEW=false"
})
@ActiveProfiles("testing")
class TestingGradingSelectionPreviewDisabledTest {
    @Autowired private StoredRecordRepository records;
    @Autowired private RecordStore store;

    @Test
    void everyOptInRecordTypeIsAbsentWhenTheFlagIsDisabled() {
        Set<String> projectIds = Set.of(
                TestingGradingSelectionPreviewConfiguration.TEACHER_PROJECT_ID.toString(),
                TestingGradingSelectionPreviewConfiguration.OTHER_TEACHER_PROJECT_ID.toString());
        Set<String> periodIds = Set.of(
                TestingGradingSelectionPreviewConfiguration.TEACHER_PERIOD_ID.toString(),
                TestingGradingSelectionPreviewConfiguration.OTHER_TEACHER_PERIOD_ID.toString());
        Set<String> types = Set.of("idea", "application_period", "allocation_run", "project_group",
                "confirmed_supervisor", "formal_application", "rubric_binding", "student_grade",
                "group_grade", "project_metric", "final_grade");
        for (String type : types) {
            assertTrue(records.findByType(type, PageRequest.of(0, 100)).getContent().stream()
                    .noneMatch(record -> projectIds.contains(record.getId().toString())
                            || contains(projectIds, record.getParentId())
                            || periodIds.contains(record.getId().toString())
                            || contains(periodIds, record.getParentId())
                            || store.parse(record).path("projectId").isTextual()
                                    && projectIds.contains(store.parse(record).path("projectId").asText())
                            || store.parse(record).path("result").path("assignments").valueStream()
                                    .anyMatch(assignment -> projectIds.contains(
                                            assignment.path("projectId").asText()))),
                    "The disabled grading preview must not create " + type + " records.");
        }
    }

    private boolean contains(Set<String> values, String value) {
        return value != null && values.contains(value);
    }
}
