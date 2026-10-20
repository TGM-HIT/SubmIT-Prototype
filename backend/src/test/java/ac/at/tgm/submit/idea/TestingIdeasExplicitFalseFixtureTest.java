package ac.at.tgm.submit.idea;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ac.at.tgm.submit.data.StoredRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testingideasexplicitfalse;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_MINIMAL_FIXTURES=false"
})
@ActiveProfiles("testing")
class TestingIdeasExplicitFalseFixtureTest {
    @Autowired private StoredRecordRepository records;

    @Test
    void explicitFalseKeepsTheFullFixtureSet() {
        PageRequest page = PageRequest.of(0, 100);
        assertEquals(5, records.findByType("application_period", page).getTotalElements());
        assertEquals(10, records.findByTypeAndStatus("idea", "ACCEPTED_FOR_APPLICATIONS", page).getTotalElements());
        assertEquals(6, records.findByType("supervision_preference", page).getTotalElements());
        assertEquals(1, records.findByType("testing_multi_group_allocation_preview", page).getTotalElements());
        assertEquals(1, records.findByType("formal_application", page).getTotalElements());
        assertEquals(20, records.findByType("idea", page).getTotalElements());
    }
}
