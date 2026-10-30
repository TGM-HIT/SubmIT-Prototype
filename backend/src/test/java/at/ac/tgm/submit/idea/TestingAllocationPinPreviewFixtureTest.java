package at.ac.tgm.submit.idea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.data.StoredRecordRepository;
import at.ac.tgm.submit.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:allocationpinpreviewfixture;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_ALLOCATION_PIN_PREVIEW=true"
})
@ActiveProfiles("testing")
class TestingAllocationPinPreviewFixtureTest {
    private static final String FIXTURE_TYPE = "testing_allocation_pin_pagination_preview";
    private static final String APPLICATION_TYPE = "student_application";
    private static final String PIN_TYPE = "allocation_pin";
    private static final String RUN_TYPE = "allocation_run";
    private static final String PIN_REASON = "PINNED_PROJECT_CANNOT_FORM_GROUP";
    private static final UUID FIXTURE_MARKER = UUID.nameUUIDFromBytes(
            "testing-allocation-preview:period".getBytes(StandardCharsets.UTF_8));

    @Autowired
    private StoredRecordRepository records;
    @Autowired
    private RecordStore store;
    @Autowired
    private UserAccountRepository users;

    @Test
    void optInStartupFixturePersistsPinPaginationAndInfeasibleRunScenario() {
        StoredRecord fixtureMarker = records.findByTypeAndParentId(FIXTURE_TYPE, FIXTURE_MARKER.toString())
                .stream().findFirst().orElseThrow();
        JsonNode fixture = store.parse(fixtureMarker);
        UUID periodId = UUID.fromString(fixture.path("periodId").asText());
        UUID runId = UUID.fromString(fixture.path("runId").asText());
        UUID pinId = UUID.fromString(fixture.path("laterPagePinId").asText());
        UUID applicationId = UUID.fromString(fixture.path("laterPageApplicationId").asText());
        UUID studentId = UUID.fromString(fixture.path("pinnedStudentId").asText());

        StoredRecord period = records.findById(periodId).orElseThrow();
        StoredRecord run = records.findById(runId).orElseThrow();
        StoredRecord laterPagePin = records.findById(pinId).orElseThrow();
        StoredRecord laterPageApplication = records.findById(applicationId).orElseThrow();
        assertEquals("ALLOCATED", period.getStatus());
        assertEquals("COMPLETED", run.getStatus());
        assertFalse("FINALIZED".equals(period.getStatus()));
        assertEquals(periodId.toString(), run.getParentId());
        assertEquals(22, fixture.path("applicationCount").asInt());
        assertEquals(21, fixture.path("pinCount").asInt());

        var applications = records.findByTypeAndParentId(APPLICATION_TYPE, periodId.toString());
        assertEquals(22, applications.size());
        assertTrue(applications.stream().allMatch(application -> "SUBMITTED".equals(application.getStatus())));
        var pins = records.findByTypeAndParentId(PIN_TYPE, periodId.toString());
        assertEquals(21, pins.size());
        assertTrue(pins.stream().anyMatch(pin -> pin.getId().equals(pinId)));

        var student = users.findById(studentId).orElseThrow();
        assertEquals("student21", student.getUsername());
        assertEquals(periodId.toString(), laterPagePin.getParentId());
        assertEquals(studentId.toString(), laterPagePin.getOwnerId());
        assertEquals(periodId.toString(), laterPageApplication.getParentId());
        assertEquals(studentId.toString(), laterPageApplication.getOwnerId());

        JsonNode pin = store.parse(laterPagePin);
        String pinnedProjectId = pin.path("projectId").asText();
        JsonNode preferences = store.parse(laterPageApplication).path("preferences");
        assertEquals(pinnedProjectId, preferences.get(1).path("projectId").asText());
        assertEquals(2, preferences.get(1).path("rank").asInt());

        JsonNode result = store.parse(run).path("result");
        JsonNode unassignedStudent = findByStudentId(result.path("unassignedStudents"), studentId);
        assertEquals(PIN_REASON, unassignedStudent.path("reason").asText());
        assertTrue(unassignedStudent.path("explanation").asText().toLowerCase().contains("pin"));
    }

    private JsonNode findByStudentId(JsonNode students, UUID studentId) {
        for (JsonNode student : students) {
            if (studentId.toString().equals(student.path("studentId").asText())) {
                return student;
            }
        }
        throw new AssertionError("Expected pinned student to be unassigned in the persisted allocation run.");
    }
}
