package at.ac.tgm.submit.idea;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecordRepository;
import at.ac.tgm.submit.user.UserAccountRepository;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
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
    private RecordStore store;
    @Autowired
    private TestingIdeasFixtureSeeder seeder;
    @Autowired
    private UserAccountRepository users;

    @Test
    void multiGroupPreviewHasFixedGroupsPreliminaryAndFinalStatesAndIsIdempotent() throws Exception {
        UUID fixtureMarker = UUID.nameUUIDFromBytes(
                "testing-allocation-preview:period".getBytes(StandardCharsets.UTF_8));
        var scenario = records.findByTypeAndParentId("testing_multi_group_allocation_preview",
                fixtureMarker.toString()).stream().findFirst().orElseThrow();
        JsonNode fixture = store.parse(scenario);
        String preliminaryPeriodId = fixture.path("preliminaryPeriodId").asText();
        String finalPeriodId = fixture.path("finalPeriodId").asText();
        var preliminaryPeriod = records.findById(UUID.fromString(preliminaryPeriodId)).orElseThrow();
        var finalPeriod = records.findById(UUID.fromString(finalPeriodId)).orElseThrow();
        var preliminaryRun = records.findById(UUID.fromString(fixture.path("preliminaryRunId").asText())).orElseThrow();
        var finalRun = records.findById(UUID.fromString(fixture.path("finalRunId").asText())).orElseThrow();
        assertEquals("2026/2027", fixture.path("schoolYear").asText());
        assertEquals("2026/2027", store.parse(preliminaryPeriod).path("schoolYear").asText());
        assertEquals("2026/2027", store.parse(finalPeriod).path("schoolYear").asText());
        assertEquals("Multi-group preliminary", store.parse(preliminaryPeriod).path("label").asText());
        assertEquals("Multi-group finalized", store.parse(finalPeriod).path("label").asText());
        assertEquals("ALLOCATED", preliminaryPeriod.getStatus());
        assertEquals("COMPLETED", preliminaryRun.getStatus());
        assertEquals("FINALIZED", finalPeriod.getStatus());
        assertEquals("FINALIZED", finalRun.getStatus());
        for (String username : List.of("student06", "student09", "student12")) {
            JsonNode visiblePeriods = objectMapper.readTree(mockMvc.perform(get("/api/application-periods")
                            .header("Authorization", "Bearer " + login(username)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("content");
            assertTrue(java.util.stream.StreamSupport.stream(visiblePeriods.spliterator(), false)
                    .anyMatch(period -> "Multi-group preliminary".equals(period.path("label").asText())));
            assertTrue(java.util.stream.StreamSupport.stream(visiblePeriods.spliterator(), false)
                    .anyMatch(period -> "Multi-group finalized".equals(period.path("label").asText())));
        }
        assertEquals(7, records.findByTypeAndParentId("student_application", preliminaryPeriodId).size());
        assertEquals(7, records.findByTypeAndParentId("student_application", finalPeriodId).size());

        Set<String> firstGroup = Set.of("student06", "student07", "student08");
        Set<String> secondGroup = Set.of("student09", "student10", "student11");
        Map<String, Set<String>> preliminaryGroups = groupsByProject(
                store.parse(preliminaryRun).path("result").path("assignments"));
        assertEquals(Set.of(firstGroup, secondGroup), new HashSet<>(preliminaryGroups.values()));
        JsonNode unassigned = store.parse(preliminaryRun).path("result").path("unassignedStudents");
        assertTrue(java.util.stream.StreamSupport.stream(unassigned.spliterator(), false)
                .map(outcome -> users.findById(UUID.fromString(outcome.path("studentId").asText()))
                        .orElseThrow().getUsername())
                .anyMatch("student12"::equals));
        assertEquals(2, preliminaryGroups.size());

        Map<String, Set<String>> finalizedGroups = new HashMap<>();
        for (var group : records.findByType("project_group", org.springframework.data.domain.PageRequest.of(0, 100))) {
            JsonNode payload = store.parse(group);
            if (finalPeriodId.equals(payload.path("periodId").asText())
                    && finalRun.getId().toString().equals(payload.path("allocationRunId").asText())) {
                Set<String> members = new HashSet<>();
                payload.path("studentIds").forEach(id -> members.add(users.findById(UUID.fromString(id.asText()))
                        .orElseThrow().getUsername()));
                finalizedGroups.put(payload.path("projectId").asText(), members);
            }
        }
        assertEquals(new HashSet<>(Set.of(firstGroup, secondGroup)), new HashSet<>(finalizedGroups.values()));
        assertEquals(preliminaryGroups.values().stream().map(HashSet::new).collect(java.util.stream.Collectors.toSet()),
                finalizedGroups.values().stream().map(HashSet::new).collect(java.util.stream.Collectors.toSet()));

        String preliminarySnapshot = preliminaryRun.getPayload();
        String finalizedSnapshot = finalRun.getPayload();
        long ideaCount = records.findByType("idea", org.springframework.data.domain.PageRequest.of(0, 100))
                .getTotalElements();
        long periodCount = records.findByType("application_period", org.springframework.data.domain.PageRequest.of(0, 100))
                .getTotalElements();
        String originalPreviewPeriodId = store.parse(records.findByTypeAndParentId(
                "testing_idea_fixture", fixtureMarker.toString()).get(0)).path("periodId").asText();
        var originalPreviewPeriod = records.findById(UUID.fromString(originalPreviewPeriodId)).orElseThrow();
        var originalPreviewRun = records.findByTypeAndParentId("allocation_run", originalPreviewPeriodId).stream()
                .filter(run -> "COMPLETED".equals(run.getStatus())).findFirst().orElseThrow();
        String originalPreviewSnapshot = originalPreviewRun.getPayload();
        seeder.run(new DefaultApplicationArguments(new String[0]));
        assertEquals(1, records.findByTypeAndParentId("testing_multi_group_allocation_preview",
                fixtureMarker.toString()).size());
        assertEquals(7, records.findByTypeAndParentId("student_application", preliminaryPeriodId).size());
        assertEquals(7, records.findByTypeAndParentId("student_application", finalPeriodId).size());
        assertEquals(1, records.findByTypeAndParentId("allocation_run", preliminaryPeriodId).size());
        assertEquals(1, records.findByTypeAndParentId("allocation_run", finalPeriodId).size());
        assertEquals(2, records.findByType("project_group", org.springframework.data.domain.PageRequest.of(0, 100))
                .stream().filter(group -> finalPeriodId.equals(store.parse(group).path("periodId").asText())
                        && finalRun.getId().toString().equals(store.parse(group).path("allocationRunId").asText()))
                .count());
        assertEquals(ideaCount, records.findByType("idea", org.springframework.data.domain.PageRequest.of(0, 100))
                .getTotalElements());
        assertEquals(periodCount, records.findByType("application_period",
                org.springframework.data.domain.PageRequest.of(0, 100)).getTotalElements());
        assertEquals(preliminarySnapshot, records.findById(preliminaryRun.getId()).orElseThrow().getPayload());
        assertEquals(finalizedSnapshot, records.findById(finalRun.getId()).orElseThrow().getPayload());
        assertEquals("ALLOCATED", records.findById(originalPreviewPeriod.getId()).orElseThrow().getStatus());
        assertEquals(originalPreviewSnapshot, records.findById(originalPreviewRun.getId()).orElseThrow().getPayload());
        assertEquals(2, finalizedGroups.size());
    }

    private Map<String, Set<String>> groupsByProject(JsonNode assignments) {
        Map<String, Set<String>> groups = new HashMap<>();
        assignments.forEach(assignment -> {
            String projectId = assignment.path("projectId").asText();
            String username = users.findById(UUID.fromString(assignment.path("studentId").asText()))
                    .orElseThrow().getUsername();
            groups.computeIfAbsent(projectId, ignored -> new HashSet<>()).add(username);
        });
        return groups;
    }

    @Test
    void allocationPreviewContainsSubmittedApplicationsAndServiceGeneratedCompletedRun() {
        UUID markerId = UUID.nameUUIDFromBytes(
                "testing-allocation-preview:period".getBytes(StandardCharsets.UTF_8));
        var fixture = records.findByTypeAndParentId("testing_idea_fixture", markerId.toString())
                .stream().findFirst().orElseThrow();
        String periodId = store.parse(fixture).path("periodId").asText();
        var period = records.findById(UUID.fromString(periodId)).orElseThrow();
        assertEquals("ALLOCATED", period.getStatus());
        assertEquals("Allocation preview", store.parse(period).path("label").asText());
        assertEquals("2026/2027", store.parse(period).path("schoolYear").asText());

        List<at.ac.tgm.submit.data.StoredRecord> applications =
                records.findByTypeAndParentId("student_application", periodId);
        assertEquals(3, applications.size());
        assertTrue(applications.stream().allMatch(application -> "SUBMITTED".equals(application.getStatus())));

        var run = records.findByTypeAndParentId("allocation_run", periodId).stream()
                .filter(candidate -> "COMPLETED".equals(candidate.getStatus()))
                .findFirst().orElseThrow();
        assertEquals(run.getId().toString(), fixture.getOwnerId());
        assertEquals(markerId.toString(), fixture.getParentId());
        assertEquals(periodId, run.getParentId());
        JsonNode runPayload = store.parse(run);
        JsonNode catalogueProjectIds = store.parse(period).path("projectIds");
        assertEquals(3, catalogueProjectIds.size());
        assertEquals(catalogueProjectIds, runPayload.path("projectIds"));
        assertEquals(3, runPayload.path("applications").size());
        assertFalse(runPayload.path("applications").isEmpty());
        assertEquals(3, runPayload.path("result").path("assignments").size());
        assertEquals(2, runPayload.path("result").path("unassignedStudents").size());
        for (JsonNode outcome : runPayload.path("result").path("unassignedStudents")) {
            assertFalse(runPayload.path("result").path("assignments").toString()
                    .contains(outcome.path("studentId").asText()));
            assertFalse(outcome.path("reason").asText().isBlank());
        }
        for (JsonNode projectId : catalogueProjectIds) {
            assertTrue(records.findByTypeAndParentId("confirmed_supervisor", projectId.asText()).isEmpty());
        }
    }

    @Test
    void assignmentPreviewAddsSyntheticUnassignedAndNoResultStudents() throws Exception {
        String unassignedToken = login("student04");
        JsonNode unassignedPeriods = objectMapper.readTree(mockMvc.perform(get("/api/application-periods")
                        .header("Authorization", "Bearer " + unassignedToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("content");
        JsonNode unassignedPeriod = findPeriod(unassignedPeriods);
        assertEquals("NOT_ASSIGNED_PRELIMINARY", unassignedPeriod.path("studentAssignment").path("state").asText());
        assertEquals("Unassigned student", unassignedPeriod.path("label").asText());
        assertEquals("2027/2028", unassignedPeriod.path("schoolYear").asText());

        String waitingToken = login("student05");
        JsonNode waitingPeriods = objectMapper.readTree(mockMvc.perform(get("/api/application-periods")
                        .header("Authorization", "Bearer " + waitingToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("content");
        JsonNode waitingPeriod = findPeriod(waitingPeriods);
        assertEquals("NO_RESULT", waitingPeriod.path("studentAssignment").path("state").asText());
        assertEquals("Waiting for result", waitingPeriod.path("label").asText());
        assertEquals("2028/2029", waitingPeriod.path("schoolYear").asText());
    }

    private JsonNode findPeriod(JsonNode periods) {
        for (JsonNode period : periods) {
            if ("SUBMITTED".equals(period.path("studentApplicationStatus").asText())
                    && period.path("studentAssignment").isObject()) {
                return period;
            }
        }
        throw new AssertionError("Student assignment preview period was not seeded.");
    }

    @Test
    void exposesTwoValidWorkflowIdeasPerStudentToOwnersAndTeachers() throws Exception {
        String teacherToken = login("teacher01");
        JsonNode teacherList = getIdeas(teacherToken);
        assertEquals(20, teacherList.path("totalElements").asInt());
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
    void ownerCanEditAndResubmitWithdrawnIdeaWhileHistoryIsPreserved() throws Exception {
        String studentToken = login("student01");
        JsonNode ideas = getIdeas(studentToken).path("content");
        JsonNode revision = null;
        for (JsonNode idea : ideas) {
            if ("IDEA_SUBMITTED".equals(idea.path("status").asText())) {
                revision = getIdea(studentToken, idea.path("id").asText());
                break;
            }
        }
        assertTrue(revision != null);
        String id = revision.path("id").asText();
        String teacherToken = login("teacher01");
        mockMvc.perform(post("/api/ideas/{id}/reviews", id)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REQUEST_REVISION\",\"feedback\":\"Clarify the model.\","
                                + "\"workSize\":4,\"feasibility\":5,\"learningValue\":4}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/ideas/{id}/supervision-preference", id)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preference\":\"WILLING_TO_SUPERVISE\",\"note\":\"Available for this project\"}"))
                .andExpect(status().isOk());
        JsonNode beforeWithdraw = getIdea(studentToken, id);
        JsonNode reviewBefore = beforeWithdraw.path("reviews").get(0).deepCopy();
        JsonNode preferenceBefore = beforeWithdraw.path("supervisionPreferences").get(0).deepCopy();
        mockMvc.perform(post("/api/ideas/{id}/withdraw", id)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk());

        var edited = revision.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) edited).put("title", "Reopened idea");
        mockMvc.perform(put("/api/ideas/{id}", id)
                        .header("Authorization", "Bearer " + studentToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(edited.toString()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("IDEA_DRAFT"));
        mockMvc.perform(post("/api/ideas/{id}/submit", id)
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("IDEA_SUBMITTED"));
        JsonNode resubmitted = getIdea(teacherToken, id);
        assertEquals("IDEA_SUBMITTED", resubmitted.path("status").asText());
        assertEquals(1, resubmitted.path("reviews").size());
        assertEquals(reviewBefore, resubmitted.path("reviews").get(0));
        assertEquals(1, resubmitted.path("supervisionPreferences").size());
        assertEquals(preferenceBefore, resubmitted.path("supervisionPreferences").get(0));
        assertTrue(java.util.stream.StreamSupport.stream(getIdeas(teacherToken).path("content").spliterator(), false)
                .anyMatch(item -> id.equals(item.path("id").asText())
                        && "IDEA_SUBMITTED".equals(item.path("status").asText())));
    }

    @Test
    void invalidWithdrawnEditLeavesPayloadAndRelatedRecordsUnchanged() throws Exception {
        String token = login("student01");
        JsonNode source = getIdea(token, getIdeas(token).path("content").get(0).path("id").asText());
        String id = source.path("id").asText();
        mockMvc.perform(post("/api/ideas/{id}/withdraw", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        JsonNode before = getIdea(token, id);
        var invalid = before.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) invalid).remove("title");
        mockMvc.perform(put("/api/ideas/{id}", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(invalid.toString()))
                .andExpect(status().isBadRequest());
        JsonNode after = getIdea(token, id);
        assertEquals("WITHDRAWN", after.path("status").asText());
        assertEquals(before.path("reviews"), after.path("reviews"));
        assertEquals(before.path("supervisionPreferences"), after.path("supervisionPreferences"));
        assertEquals(before.path("title"), after.path("title"));
    }

    @Test
    void directWithdrawnResubmissionRejectsInvalidStoredPayloadWithoutChangingState() throws Exception {
        String token = login("student01");
        JsonNode source = getIdea(token, getIdeas(token).path("content").get(0).path("id").asText());
        String id = source.path("id").asText();
        mockMvc.perform(post("/api/ideas/{id}/withdraw", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        var record = records.findById(UUID.fromString(id)).orElseThrow();
        var invalid = store.parse(record);
        invalid.remove("title");
        record.setPayload(invalid.toString());
        records.save(record);

        mockMvc.perform(post("/api/ideas/{id}/submit", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        assertEquals("WITHDRAWN", records.findById(UUID.fromString(id)).orElseThrow().getStatus());
    }

    @Test
    void withdrawnSpecificationCanBeEditedAndResubmittedThroughExistingLifecycle() throws Exception {
        String token = login("student01");
        JsonNode specified = null;
        for (JsonNode candidate : getIdeas(token).path("content")) {
            if ("SPECIFICATION_SUBMITTED".equals(candidate.path("status").asText())) {
                specified = candidate;
                break;
            }
        }
        assertTrue(specified != null);
        String id = specified.path("id").asText();
        JsonNode detail = getIdea(token, id);
        JsonNode existingReviews = detail.path("reviews").deepCopy();
        mockMvc.perform(post("/api/ideas/{id}/withdraw", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        JsonNode spec = detail.path("specification");
        ((com.fasterxml.jackson.databind.node.ObjectNode) spec).put("scope", "Changed scope for the resubmitted specification.");
        mockMvc.perform(put("/api/ideas/{id}/specification", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(spec.toString()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/ideas/{id}/specification/submit", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        JsonNode resubmitted = getIdea(login("teacher01"), id);
        assertEquals("SPECIFICATION_SUBMITTED", resubmitted.path("status").asText());
        assertEquals(existingReviews, resubmitted.path("reviews"));
        assertEquals("Changed scope for the resubmitted specification.",
                resubmitted.path("specification").path("scope").asText());
    }

    @Test
    void genericWithdrawnIdeaEndpointsCannotChangeWithdrawnFullSpecificationInAnyChildState() throws Exception {
        String token = login("student01");
        JsonNode specified = java.util.stream.StreamSupport.stream(
                        getIdeas(token).path("content").spliterator(), false)
                .filter(candidate -> "SPECIFICATION_SUBMITTED".equals(candidate.path("status").asText()))
                .findFirst().orElseThrow();
        String id = specified.path("id").asText();
        JsonNode before = getIdea(token, id);
        JsonNode specificationBefore = before.path("specification").deepCopy();
        var specificationRecord = records.findById(UUID.fromString(specificationBefore.path("id").asText())).orElseThrow();

        mockMvc.perform(post("/api/ideas/{id}/withdraw", id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        for (String childState : List.of("SPECIFICATION_SUBMITTED", "SPECIFICATION_NEEDS_REVISION",
                "SPECIFICATION_DRAFT")) {
            specificationRecord.setStatus(childState);
            records.save(specificationRecord);
            JsonNode withdrawn = getIdea(token, id);
            assertEquals("WITHDRAWN", withdrawn.path("status").asText());
            assertEquals(childState, withdrawn.path("specification").path("status").asText());
            assertEquals(specificationBefore.path("objectives"), withdrawn.path("specification").path("objectives"));

            mockMvc.perform(put("/api/ideas/{id}", id)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(withdrawn.toString()))
                    .andExpect(status().isConflict());
            mockMvc.perform(post("/api/ideas/{id}/submit", id)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());

            JsonNode after = getIdea(token, id);
            assertEquals("WITHDRAWN", after.path("status").asText());
            assertEquals(withdrawn.path("specification"), after.path("specification"));
        }
    }

    @Test
    void onlyTheOwnerStudentCanEditOrResubmitAWithdrawnIdea() throws Exception {
        String ownerToken = login("student01");
        JsonNode source = getIdea(ownerToken, getIdeas(ownerToken).path("content").get(0).path("id").asText());
        String id = source.path("id").asText();
        mockMvc.perform(post("/api/ideas/{id}/withdraw", id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk());
        String payload = source.toString();
        for (String username : List.of("student02", "teacher01", "admin01")) {
            mockMvc.perform(put("/api/ideas/{id}", id)
                            .header("Authorization", "Bearer " + login(username))
                            .contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/ideas/{id}/submit", id)
                            .header("Authorization", "Bearer " + login(username)))
                    .andExpect(status().isForbidden());
        }
        assertEquals("WITHDRAWN", getIdea(ownerToken, id).path("status").asText());
    }

    @Test
    void ownedIdeasInOtherLifecycleStatesRejectEditAndSubmitWithoutMutation() throws Exception {
        String token = login("student01");
        String teacherToken = login("teacher01");
        String submittedId = createAndSubmit(token, "Matrix submitted");
        String approvedId = createAndSubmit(token, "Matrix approved");
        mockMvc.perform(post("/api/ideas/{id}/reviews", approvedId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVE_FOR_SPECIFICATION\",\"feedback\":\"Approved.\"}"))
                .andExpect(status().isCreated());
        String declinedId = createAndSubmit(token, "Matrix declined");
        mockMvc.perform(post("/api/ideas/{id}/reviews", declinedId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"DECLINE\",\"feedback\":\"Declined.\"}"))
                .andExpect(status().isCreated());
        String acceptedId = java.util.stream.StreamSupport.stream(getIdeas(token).path("content").spliterator(), false)
                .filter(item -> "ACCEPTED_FOR_APPLICATIONS".equals(item.path("status").asText()))
                .findFirst().orElseThrow().path("id").asText();

        for (String id : List.of(submittedId, approvedId, declinedId, acceptedId)) {
            JsonNode before = getIdea(token, id);
            JsonNode replacement = before.deepCopy();
            ((com.fasterxml.jackson.databind.node.ObjectNode) replacement).put("title", "Should not change");
            mockMvc.perform(put("/api/ideas/{id}", id)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content(replacement.toString()))
                    .andExpect(status().isConflict());
            mockMvc.perform(post("/api/ideas/{id}/submit", id)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isConflict());
            JsonNode after = getIdea(token, id);
            assertEquals(before.path("status"), after.path("status"), id);
            assertEquals(before.path("title"), after.path("title"), id);
            assertEquals(before.path("description"), after.path("description"), id);
            assertEquals(before.path("reviews"), after.path("reviews"), id);
            assertEquals(before.path("supervisionPreferences"), after.path("supervisionPreferences"), id);
        }
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

    private String createAndSubmit(String token, String title) throws Exception {
        String payload = "{\"title\":\"" + title + "\",\"description\":\"A valid description.\","
                + "\"intendedOutcome\":\"A valid outcome.\",\"schoolYear\":\"2026-2027\","
                + "\"scopePoints\":[\"A valid scope point\"],\"learningTopics\":[]}";
        MvcResult created = mockMvc.perform(post("/api/ideas")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated()).andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText();
        mockMvc.perform(post("/api/ideas/{id}/submit", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        return id;
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
