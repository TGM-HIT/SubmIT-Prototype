package ac.at.tgm.submit.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.user.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:applyingpreview;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "dadb.testing.applying-group-preview=true"})
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class TestingApplyingGroupPreviewTest {
    @Autowired RecordStore store;
    @Autowired UserAccountRepository users;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;

    @Test
    void G8_optInPersistedPrerequisitesAndOrdinaryAccountCounts() throws Exception {
        assertEquals(32, users.count());
        var marker = store.repository().findByType("testing_applying_group_preview",
                org.springframework.data.domain.PageRequest.of(0, 20)).getContent().getFirst();
        var details = store.parse(marker);
        UUID main = UUID.fromString(details.path("mainPeriodId").textValue());
        UUID departure = UUID.fromString(details.path("departurePeriodId").textValue());
        var period = store.get("application_period", main);
        assertEquals("OPEN", period.getStatus());
        assertTrue(Instant.parse(store.parse(period).path("closesAt").textValue()).isAfter(Instant.now()));
        assertEquals(3, store.parse(period).path("projectIds").size());
        assertTrue(store.repository().findByTypeAndParentId(ApplyingGroupRecords.TYPE, main.toString()).isEmpty());
        for (UUID id : List.of(main, departure)) {
            var applications = store.repository().findByTypeAndParentId("student_application", id.toString());
            assertEquals(3, applications.size());
            assertTrue(applications.stream().allMatch(record -> store.parse(record).path("preferences").size() == 3));
        }
        var shared = store.repository().findByTypeAndParentId(ApplyingGroupRecords.TYPE, departure.toString()).getFirst();
        var data = store.parse(shared);
        assertEquals("SUBMITTED", shared.getStatus());
        assertEquals(2, data.path("members").size());
        assertEquals(1, data.path("invited").size());
        for (var member : data.path("members")) assertEquals(data.path("revision").longValue(),
                data.path("approvals").path(member.textValue()).longValue());
        var run = store.repository().findByTypeAndParentId("allocation_run", departure.toString()).getFirst();
        assertEquals("COMPLETED", run.getStatus());
        assertEquals(1, store.parse(run).path("applyingUnits").size());
        assertEquals(3, store.parse(run).path("result").path("assignments").size());
        var login = http.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"student01\",\"password\":\"testing-password\"}")).andExpect(status().isOk())
                .andReturn().getResponse();
        var token = json.readTree(login.getContentAsString()).path("token").textValue();
        for (UUID id : List.of(main, departure)) {
            http.perform(get("/api/application-periods/" + id + "/applications/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
        var adminLogin = http.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"username\":\"admin01\",\"password\":\"testing-password\"}")).andExpect(status().isOk())
                .andReturn().getResponse();
        var adminToken = json.readTree(adminLogin.getContentAsString()).path("token").textValue();
        var catalogue = http.perform(get("/api/ideas").param("page", "0").param("size", "100")
                .param("status", "ACCEPTED_FOR_APPLICATIONS").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse();
        var content = json.readTree(catalogue.getContentAsString()).get("content");
        assertNotNull(content);
        assertTrue(content.isArray());
        for (var project : store.parse(period).path("projectIds")) {
            assertTrue(java.util.stream.StreamSupport.stream(content.spliterator(), false)
                    .anyMatch(idea -> idea.path("id").textValue().equals(project.textValue())));
        }
    }
}
