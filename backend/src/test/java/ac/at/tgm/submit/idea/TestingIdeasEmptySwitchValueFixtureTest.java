package ac.at.tgm.submit.idea;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ac.at.tgm.submit.data.StoredRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testingideasemptyproperty;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_MINIMAL_FIXTURES="
})
@ActiveProfiles("testing")
class TestingIdeasEmptySwitchValueFixtureTest {
    @Autowired private StoredRecordRepository records;

    @Test
    void emptySwitchValueKeepsTheFullFixtureSet() {
        PageRequest page = PageRequest.of(0, 100);
        assertEquals(5, records.findByType("application_period", page).getTotalElements());
        assertEquals(10, records.findByTypeAndStatus("idea", "ACCEPTED_FOR_APPLICATIONS", page).getTotalElements());
    }
}
