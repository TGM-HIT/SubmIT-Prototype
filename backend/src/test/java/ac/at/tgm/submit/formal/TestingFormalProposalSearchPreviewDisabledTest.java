package ac.at.tgm.submit.formal;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ac.at.tgm.submit.data.StoredRecordRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:formalproposalpreviewdisabled;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_FORMAL_PROPOSAL_SEARCH_PREVIEW=false"
})
@ActiveProfiles("testing")
class TestingFormalProposalSearchPreviewDisabledTest {
    @Autowired private StoredRecordRepository records;

    @Test
    void fixtureIsAbsentWhenOptInPropertyIsDisabled() {
        Set<String> projectIds = Set.of(TestingFormalProposalSearchPreviewConfiguration.TEACHER_PROJECT_ID.toString(),
                TestingFormalProposalSearchPreviewConfiguration.OTHER_TEACHER_PROJECT_ID.toString());
        for (String type : Set.of("idea", "project_group", "formal_application", "confirmed_supervisor")) {
            assertTrue(records.findByType(type, org.springframework.data.domain.PageRequest.of(0, 100))
                    .getContent().stream().noneMatch(record -> (record.getParentId() != null
                            && projectIds.contains(record.getParentId()))
                            || projectIds.contains(record.getId().toString())));
        }
    }
}
