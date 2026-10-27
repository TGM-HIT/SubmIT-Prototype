package ac.at.tgm.submit.formal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:formalproposalpreviewdisabled;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_FORMAL_PROPOSAL_SEARCH_PREVIEW=false"
})
@ActiveProfiles("testing")
class TestingFormalProposalSearchPreviewDisabledTest {
    @Autowired private StoredRecordRepository records;
    @Autowired private RecordStore store;

    @Test
    void fixtureIsAbsentWhenOptInPropertyIsDisabled() {
        Set<String> projectIds = Set.of(TestingFormalProposalSearchPreviewConfiguration.TEACHER_PROJECT_ID.toString(),
                TestingFormalProposalSearchPreviewConfiguration.OTHER_TEACHER_PROJECT_ID.toString());
        for (String type : Set.of("idea", "project_group", "formal_application", "confirmed_supervisor")) {
            assertTrue(records.findByType(type, PageRequest.of(0, 100))
                    .getContent().stream().noneMatch(record -> (record.getParentId() != null
                            && projectIds.contains(record.getParentId()))
                            || projectIds.contains(record.getId().toString())));
        }

        var periods = records.findByType("application_period", PageRequest.of(0, 100)).getContent();
        Set<String> previewPeriodIds = new HashSet<>();
        for (var period : periods) {
            JsonNode catalogue = store.parse(period).path("projectIds");
            if (catalogue.isArray() && catalogue.valueStream().anyMatch(projectId -> projectIds.contains(
                    projectId.asText()))) {
                previewPeriodIds.add(period.getId().toString());
            }
        }
        assertTrue(previewPeriodIds.isEmpty(), "Preview project catalogues must not leak application periods.");
        assertTrue(records.findByType("allocation_run", PageRequest.of(0, 100)).getContent().stream()
                .noneMatch(run -> previewPeriodIds.contains(run.getParentId())
                        || store.parse(run).path("result").path("assignments").valueStream()
                                .anyMatch(assignment -> projectIds.contains(
                                        assignment.path("projectId").asText()))),
                "No allocation run for the preview projects or periods may remain.");
    }
}
