package com.flightlog.idea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flightlog.data.StoredRecordRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:testingideas;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
@Transactional
class TestingIdeasFixtureTest {
    private static final List<String> STUDENTS = List.of(
            "student01", "student02", "student03", "student04", "student05");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StoredRecordRepository records;
    @Autowired
    private TestingIdeasFixtureSeeder seeder;

    @Test
    void exposesTwoValidWorkflowIdeasPerStudentToOwnersAndTeachers() throws Exception {
        String teacherToken = login("teacher01");
        JsonNode teacherList = getIdeas(teacherToken);
        assertEquals(13, teacherList.path("totalElements").asInt());
        Set<String> teacherIdeaIds = new HashSet<>();

        for (String username : STUDENTS) {
            String studentToken = login(username);
            JsonNode studentList = getIdeas(studentToken);
            int expectedIdeas = List.of("student01", "student02", "student03").contains(username) ? 3 : 2;
            assertEquals(expectedIdeas, studentList.path("totalElements").asInt(), username);
            assertEquals(expectedIdeas, studentList.path("content").size(), username);

            JsonNode submitted = null;
            JsonNode specified = null;
            Set<String> titles = new HashSet<>();
            for (JsonNode item : studentList.path("content")) {
                if (!"ACCEPTED_FOR_APPLICATIONS".equals(item.path("status").asText())) {
                    titles.add(item.path("title").asText());
                    teacherIdeaIds.add(item.path("id").asText());
                }
                if ("IDEA_SUBMITTED".equals(item.path("status").asText())) {
                    submitted = item;
                } else if ("SPECIFICATION_SUBMITTED".equals(item.path("status").asText())) {
                    specified = item;
                }
            }
            assertEquals(2, titles.size(), username);
            assertTrue(submitted != null, username);
            assertTrue(specified != null, username);

            JsonNode submittedDetail = getIdea(studentToken, submitted.path("id").asText());
            assertEquals("IDEA_SUBMITTED", submittedDetail.path("status").asText());
            assertEquals(0, submittedDetail.path("reviews").size());
            assertTrue(submittedDetail.path("supervisionPreferences").isEmpty());
            assertEquals("2026-2027", submittedDetail.path("schoolYear").asText());
            assertFalse(submittedDetail.path("scopePoints").isEmpty());
            assertFalse(submittedDetail.path("learningTopics").isEmpty());
            assertEquals("IDEA_SUBMITTED", getIdea(teacherToken, submitted.path("id").asText())
                    .path("status").asText());

            JsonNode specifiedDetail = getIdea(studentToken, specified.path("id").asText());
            assertEquals("SPECIFICATION_SUBMITTED", specifiedDetail.path("status").asText());
            assertEquals("SPECIFICATION_SUBMITTED", specifiedDetail.path("specification").path("status").asText());
            assertFalse(specifiedDetail.path("specification").path("objectives").isEmpty());
            assertFalse(specifiedDetail.path("specification").path("intendedUsers").isEmpty());
            assertFalse(specifiedDetail.path("specification").path("requirements").isEmpty());
            assertFalse(specifiedDetail.path("specification").path("deliverables").isEmpty());
            assertFalse(specifiedDetail.path("specification").path("scope").asText().isBlank());
            assertFalse(specifiedDetail.path("specification").path("dependencies").isEmpty());
            JsonNode reviews = specifiedDetail.path("reviews");
            assertEquals(1, reviews.size());
            assertEquals("IDEA", reviews.get(0).path("reviewType").asText());
            assertEquals("APPROVE_FOR_SPECIFICATION", reviews.get(0).path("decision").asText());
            assertEquals("Olivia Chen", reviews.get(0).path("teacherDisplayName").asText());
            assertTrue(specifiedDetail.path("supervisionPreferences").isEmpty());
            assertEquals("SPECIFICATION_SUBMITTED", getIdea(teacherToken, specified.path("id").asText())
                    .path("specification").path("status").asText());

            assertTrue(records.findByTypeAndParentId("confirmed_supervisor", submitted.path("id").asText())
                    .isEmpty());
            assertTrue(records.findByTypeAndParentId("confirmed_supervisor", specified.path("id").asText())
                    .isEmpty());
        }
        assertEquals(10, teacherIdeaIds.size());
    }

    @Test
    void teachersCanReviewBothSeededStagesAndRerunningPreservesTheirChanges() throws Exception {
        String teacherToken = login("teacher01");
        JsonNode studentIdeas = getIdeas(login("student01")).path("content");
        String submittedId = null;
        String specifiedId = null;
        for (JsonNode idea : studentIdeas) {
            if ("IDEA_SUBMITTED".equals(idea.path("status").asText())) {
                submittedId = idea.path("id").asText();
            } else if ("SPECIFICATION_SUBMITTED".equals(idea.path("status").asText())) {
                specifiedId = idea.path("id").asText();
            }
        }
        assertTrue(submittedId != null);
        assertTrue(specifiedId != null);

        mockMvc.perform(post("/api/ideas/{id}/reviews", submittedId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REQUEST_REVISION\",\"feedback\":\"Clarify the model.\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/ideas/{id}/specification/reviews", specifiedId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REQUEST_REVISION\",\"feedback\":\"Clarify the scope.\","
                                + "\"workSize\":1,\"curriculumRelevance\":1,\"technicalComplexity\":1,"
                                + "\"feasibility\":1,\"learningValue\":1,\"resourceNeeds\":1,"
                                + "\"riskAndDependencies\":1}"))
                .andExpect(status().isCreated());

        assertEquals("IDEA_NEEDS_REVISION", getIdea(login("student01"), submittedId).path("status").asText());
        assertEquals("SPECIFICATION_NEEDS_REVISION", getIdea(login("student01"), specifiedId)
                .path("status").asText());
        assertEquals(1, records.findByTypeAndParentId("specification", specifiedId).size());
        assertEquals(2, records.findByTypeAndParentId("review", specifiedId).size());
        seeder.run(new DefaultApplicationArguments(new String[0]));

        JsonNode afterRerun = getIdeas(login("student01"));
        assertEquals(3, afterRerun.path("totalElements").asInt());
        assertEquals(3, afterRerun.path("content").size());
        assertNotEquals(submittedId, specifiedId);
        assertEquals("IDEA_NEEDS_REVISION", getIdea(login("student01"), submittedId).path("status").asText());
        assertEquals("SPECIFICATION_NEEDS_REVISION", getIdea(login("student01"), specifiedId)
                .path("status").asText());
        assertEquals(1, records.findByTypeAndParentId("specification", specifiedId).size());
        assertEquals(2, records.findByTypeAndParentId("review", specifiedId).size());
    }

    private JsonNode getIdeas(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ideas").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getIdea(String token, String id) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/ideas/{id}", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"testing-password\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }
}
