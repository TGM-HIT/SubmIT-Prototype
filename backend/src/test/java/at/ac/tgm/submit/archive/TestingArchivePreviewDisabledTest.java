package at.ac.tgm.submit.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import at.ac.tgm.submit.data.StoredRecordRepository;
import at.ac.tgm.submit.user.UserAccountRepository;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:archivepreviewdisabled;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "dadb.testing.archive-preview=false"
})
@ActiveProfiles("testing")
class TestingArchivePreviewDisabledTest {
    @Autowired private StoredRecordRepository records;
    @Autowired private UserAccountRepository users;

    @Test
    void optInFixtureIsAbsentAndDefaultTestingAccountCountIsUnchanged() {
        assertEquals(0, records.findByType("testing_archive_preview", PageRequest.of(0, 10)).getTotalElements());
        assertFalse(records.findByType("project", PageRequest.of(0, 100)).getContent().stream()
                .map(record -> record.getPayload())
                .anyMatch(payload -> payload.contains("Reviewed website archive preview")));
        assertEquals(32, users.count());
    }
}
