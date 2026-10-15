package com.flightlog.formal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.data.StoredRecordRepository;
import com.flightlog.user.UserAccount;
import com.flightlog.user.UserAccountRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:testingformalfixture;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class TestingFormalApplicationFixtureTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserAccountRepository users;
    @Autowired
    private StoredRecordRepository records;
    @Autowired
    private RecordStore store;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    @Qualifier("seedTestingFormalApplicationFixture")
    private ApplicationRunner fixtureSeeder;

    @Test
    void fixtureIsAllocatedSubmittedAndReachableByAnAuthenticatedTeacher() throws Exception {
        StoredRecord group = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc("project_group",
                TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString()).orElseThrow();
        StoredRecord application = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc("formal_application",
                TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString()).orElseThrow();
        JsonNode groupData = store.parse(group);
        JsonNode applicationData = store.parse(application);

        assertEquals("project_group", group.getType());
        assertEquals("ALLOCATED", group.getStatus());
        assertEquals(TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString(), group.getParentId());
        assertEquals(3, groupData.path("studentIds").size());
        assertEquals("formal_application", application.getType());
        assertEquals("SUBMITTED", application.getStatus());
        assertEquals(TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString(), application.getParentId());
        assertEquals(3, applicationData.path("individualTopics").size());
        assertEquals(3, applicationData.path("investigationConcerns").size());
        assertEquals(3, applicationData.path("plannedResults").size());
        assertTrue(applicationData.path("topic").asText().contains("Synthetic"));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"teacher01\",\"password\":\"testing-password\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("TEACHER"))
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).path("token").asText();
        mockMvc.perform(get("/api/projects/{projectId}/formal-applications",
                        TestingFormalApplicationFixtureConfiguration.PROJECT_ID)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(application.getId().toString()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.individualTopics.length()").value(3));
    }

    @Test
    void fixtureSeederCanRunRepeatedlyWithoutChangingOrDuplicatingFixtureData() throws Exception {
        ApplicationArguments args = new DefaultApplicationArguments(new String[0]);
        StoredRecord group = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc("project_group",
                TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString()).orElseThrow();
        StoredRecord application = records.findFirstByTypeAndParentIdOrderByCreatedAtDesc("formal_application",
                TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString()).orElseThrow();
        JsonNode originalGroup = store.parse(group);
        JsonNode originalApplication = store.parse(application);

        fixtureSeeder.run(args);
        fixtureSeeder.run(args);

        assertEquals(group.getId(), records.findFirstByTypeAndParentIdOrderByCreatedAtDesc("project_group",
                TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString()).orElseThrow().getId());
        assertEquals(application.getId(), records.findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "formal_application", TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString())
                .orElseThrow().getId());
        assertEquals(originalGroup, store.parse(records.findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "project_group", TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString()).orElseThrow()));
        assertEquals(originalApplication, store.parse(records.findFirstByTypeAndParentIdOrderByCreatedAtDesc(
                "formal_application", TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString())
                .orElseThrow()));
        assertEquals(1, records.findByTypeAndParentId("project_group",
                TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString()).size());
        assertEquals(1, records.findByTypeAndParentId("formal_application",
                TestingFormalApplicationFixtureConfiguration.PROJECT_ID.toString()).size());
        assertEquals(32, users.count());
        List<String> seededStudentIds = List.of("student01", "student02", "student03").stream()
                .map(username -> users.findByUsername(username).orElseThrow().getId().toString())
                .toList();
        List<String> fixtureStudentIds = List.of(
                originalGroup.path("studentIds").get(0).asText(),
                originalGroup.path("studentIds").get(1).asText(),
                originalGroup.path("studentIds").get(2).asText());
        assertEquals(seededStudentIds, fixtureStudentIds);
    }
}
