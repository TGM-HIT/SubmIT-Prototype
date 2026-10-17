package com.flightlog.formal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flightlog.data.RecordStore;
import com.flightlog.data.StoredRecord;
import com.flightlog.data.StoredRecordRepository;
import com.flightlog.user.UserAccountRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:formalvalidationapi;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class FormalApplicationValidationApiTest {
    private static final List<String> MEMBER_USERNAMES = List.of("student01", "student02", "student03");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;
    @Autowired private StoredRecordRepository records;
    @Autowired private UserAccountRepository users;

    private String studentToken;
    private List<String> memberIds;
    private String outsiderId;

    @BeforeEach
    void setUp() throws Exception {
        studentToken = login("student01");
        memberIds = MEMBER_USERNAMES.stream()
                .map(username -> users.findByUsername(username).orElseThrow().getId().toString())
                .toList();
        outsiderId = users.findByUsername("student04").orElseThrow().getId().toString();
    }

    @Test
    void requiredFieldsAndExactlyOneMemberEntryAreEnforcedOnCreateAndUpdate() throws Exception {
        List<InvalidRequest> invalidRequests = List.of(
                invalid("missing project topic", body -> body.remove("topic")),
                invalid("blank project topic", body -> body.put("topic", "  ")),
                invalid("missing initial situation", body -> body.remove("initialSituation")),
                invalid("blank initial situation", body -> body.put("initialSituation", "")),
                invalid("missing objective", body -> body.remove("objective")),
                invalid("blank objective", body -> body.put("objective", " ")),
                invalid("missing individual topics", body -> body.remove("individualTopics")),
                invalid("empty individual topics", body -> body.set("individualTopics", objectMapper.createArrayNode())),
                invalid("missing allocated member individual topic",
                        body -> ((ArrayNode) body.path("individualTopics")).remove(2)),
                invalid("duplicate individual-topic member",
                        body -> setEntryMember(body, "individualTopics", 2, memberIds.get(0))),
                invalid("non-member individual-topic ID",
                        body -> setEntryMember(body, "individualTopics", 2, outsiderId)),
                invalid("missing individual-topic member ID",
                        body -> ((ObjectNode) body.path("individualTopics").get(0)).remove("studentId")),
                invalid("missing individual-topic text",
                        body -> ((ObjectNode) body.path("individualTopics").get(0)).remove("topic")),
                invalid("blank individual topic",
                        body -> ((ObjectNode) body.path("individualTopics").get(0)).put("topic", "")),
                invalid("missing investigation concerns", body -> body.remove("investigationConcerns")),
                invalid("empty investigation concerns",
                        body -> body.set("investigationConcerns", objectMapper.createArrayNode())),
                invalid("missing allocated member investigation concern",
                        body -> ((ArrayNode) body.path("investigationConcerns")).remove(2)),
                invalid("duplicate investigation-concern member",
                        body -> setEntryMember(body, "investigationConcerns", 2, memberIds.get(0))),
                invalid("non-member investigation-concern ID",
                        body -> setEntryMember(body, "investigationConcerns", 2, outsiderId)),
                invalid("missing investigation-concern member ID",
                        body -> ((ObjectNode) body.path("investigationConcerns").get(0)).remove("studentId")),
                invalid("missing investigation-concern text",
                        body -> ((ObjectNode) body.path("investigationConcerns").get(0)).remove("text")),
                invalid("blank investigation concern",
                        body -> ((ObjectNode) body.path("investigationConcerns").get(0)).put("text", " ")),
                invalid("missing planned results", body -> body.remove("plannedResults")),
                invalid("empty planned results", body -> body.set("plannedResults", objectMapper.createArrayNode())),
                invalid("missing allocated member planned result",
                        body -> ((ArrayNode) body.path("plannedResults")).remove(2)),
                invalid("duplicate planned-result member",
                        body -> setEntryMember(body, "plannedResults", 2, memberIds.get(0))),
                invalid("non-member planned-result ID",
                        body -> setEntryMember(body, "plannedResults", 2, outsiderId)),
                invalid("missing planned-result member ID",
                        body -> ((ObjectNode) body.path("plannedResults").get(0)).remove("studentId")),
                invalid("missing planned-result text",
                        body -> ((ObjectNode) body.path("plannedResults").get(0)).remove("text")),
                invalid("blank planned result",
                        body -> ((ObjectNode) body.path("plannedResults").get(0)).put("text", "")),
                invalid("missing milestones", body -> body.remove("milestones")),
                invalid("empty milestones", body -> body.set("milestones", objectMapper.createArrayNode())),
                invalid("missing milestone text",
                        body -> ((ObjectNode) body.path("milestones").get(0)).remove("text")));

        for (InvalidRequest invalidRequest : invalidRequests) {
            rejectForCreateAndUpdateWithoutMutation(invalidRequest);
        }
    }

    @Test
    void perFieldLimitsRejectOverLimitValuesOnCreateAndUpdate() throws Exception {
        List<InvalidRequest> invalidRequests = List.of(
                invalid("project topic over 200 characters", body -> body.put("topic", "t".repeat(201))),
                invalid("individual topic over 200 characters",
                        body -> ((ObjectNode) body.path("individualTopics").get(0)).put("topic", "i".repeat(201))),
                invalid("initial situation over 400 characters",
                        body -> body.put("initialSituation", "s".repeat(401))),
                invalid("objective over 400 characters", body -> body.put("objective", "o".repeat(401))));

        for (InvalidRequest invalidRequest : invalidRequests) {
            rejectForCreateAndUpdateWithoutMutation(invalidRequest);
        }
    }

    @Test
    void combinedInvestigationAndPlannedResultLimitsRejectOverLimitOnCreateAndUpdate() throws Exception {
        List<InvalidRequest> invalidRequests = List.of(
                invalid("investigation concerns total 2401 characters",
                        body -> setTextLengths(body, "investigationConcerns", 801, 800, 800)),
                invalid("planned results total 2401 characters",
                        body -> setTextLengths(body, "plannedResults", 801, 800, 800)));

        for (InvalidRequest invalidRequest : invalidRequests) {
            rejectForCreateAndUpdateWithoutMutation(invalidRequest);
        }
    }

    @Test
    void milestoneTextAndDatesAreValidatedOnCreateAndUpdate() throws Exception {
        List<InvalidRequest> invalidRequests = List.of(
                invalid("empty milestone text",
                        body -> ((ObjectNode) body.path("milestones").get(0)).put("text", "  ")),
                invalid("invalid milestone date",
                        body -> ((ObjectNode) body.path("milestones").get(0)).put("date", "2027-02-30")),
                invalid("missing milestone date",
                        body -> ((ObjectNode) body.path("milestones").get(0)).remove("date")));

        for (InvalidRequest invalidRequest : invalidRequests) {
            rejectForCreateAndUpdateWithoutMutation(invalidRequest);
        }
    }

    @Test
    void exactCharacterLimitsAndValidMilestonesAreAcceptedOnCreateAndUpdate() throws Exception {
        UUID projectId = createAllocatedProject();
        ObjectNode request = boundaryRequest();

        MvcResult created = submitCreate(projectId, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.topic").value("t".repeat(200)))
                .andReturn();
        UUID applicationId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText());
        assertPersistedBoundaryPayload(projectId, applicationId);

        ObjectNode changedRequest = boundaryRequest();
        changedRequest.put("topic", "u".repeat(200));
        mockMvc.perform(put("/api/formal-applications/{id}", applicationId)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changedRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topic").value("u".repeat(200)));

        StoredRecord persistedApplication = records.findById(applicationId).orElseThrow();
        JsonNode persistedPayload = objectMapper.readTree(persistedApplication.getPayload());
        assertEquals("u".repeat(200), persistedPayload.path("topic").asText());
        assertEquals(200, persistedPayload.path("individualTopics").get(0).path("topic").asText().length());
        assertEquals(400, persistedPayload.path("initialSituation").asText().length());
        assertEquals(400, persistedPayload.path("objective").asText().length());
        assertEquals(2400, combinedLength(persistedPayload.path("investigationConcerns")));
        assertEquals(2400, combinedLength(persistedPayload.path("plannedResults")));
        assertEquals("2027-02-01", persistedPayload.path("milestones").get(0).path("date").asText());
        assertEquals(1, records.findByTypeAndParentId("formal_application_revision", applicationId.toString()).size());
    }

    private void rejectForCreateAndUpdateWithoutMutation(InvalidRequest invalidRequest) throws Exception {
        UUID projectId = createAllocatedProject();
        ObjectNode invalidPayload = validRequest();
        invalidRequest.mutate().accept(invalidPayload);

        submitCreate(projectId, invalidPayload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
        assertTrue(records.findByTypeAndParentId("formal_application", projectId.toString()).isEmpty(),
                invalidRequest.name() + " must not create an application");

        MvcResult created = submitCreate(projectId, validRequest()).andExpect(status().isCreated()).andReturn();
        UUID applicationId = UUID.fromString(
                objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asText());
        List<RecordSnapshot> persistedBefore = persistedState(projectId, applicationId);

        mockMvc.perform(put("/api/formal-applications/{id}", applicationId)
                        .header("Authorization", bearer(studentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidPayload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));

        assertEquals(persistedBefore, persistedState(projectId, applicationId),
                invalidRequest.name() + " must not mutate the persisted application, version history, or audit history");
    }

    private List<RecordSnapshot> persistedState(UUID projectId, UUID applicationId) {
        List<StoredRecord> relevant = new ArrayList<>();
        relevant.addAll(records.findByTypeAndParentId("formal_application", projectId.toString()));
        relevant.addAll(records.findByTypeAndParentId("formal_application_revision", applicationId.toString()));
        relevant.addAll(records.findByTypeAndParentId("audit", applicationId.toString()));
        return relevant.stream()
                .map(record -> new RecordSnapshot(record.getId(), record.getType(), record.getOwnerId(),
                        record.getParentId(), record.getStatus(), record.getPayload(), record.getCreatedAt()))
                .sorted(Comparator.comparing(snapshot -> snapshot.id().toString()))
                .toList();
    }

    private void assertPersistedBoundaryPayload(UUID projectId, UUID applicationId) throws Exception {
        StoredRecord reloaded = records.findById(applicationId).orElseThrow();
        JsonNode payload = objectMapper.readTree(reloaded.getPayload());
        assertEquals(projectId.toString(), reloaded.getParentId());
        assertEquals("DRAFT", reloaded.getStatus());
        assertEquals(200, payload.path("topic").asText().length());
        assertEquals(200, payload.path("individualTopics").get(0).path("topic").asText().length());
        assertEquals(400, payload.path("initialSituation").asText().length());
        assertEquals(400, payload.path("objective").asText().length());
        assertEquals(2400, combinedLength(payload.path("investigationConcerns")));
        assertEquals(2400, combinedLength(payload.path("plannedResults")));
        assertTrue(payload.path("milestones").get(0).path("text").asText().length() > 0);
    }

    private int combinedLength(JsonNode entries) {
        int length = 0;
        for (JsonNode entry : entries) {
            length += entry.path("text").asText().length();
        }
        return length;
    }

    private ResultActions submitCreate(UUID projectId, ObjectNode request) throws Exception {
        return mockMvc.perform(post("/api/projects/{projectId}/formal-applications", projectId)
                .header("Authorization", bearer(studentToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)));
    }

    private UUID createAllocatedProject() {
        UUID projectId = UUID.randomUUID();
        ObjectNode group = store.object();
        group.set("studentIds", store.value(memberIds));
        store.create("project_group", null, projectId.toString(), "ALLOCATED", group);
        return projectId;
    }

    private ObjectNode validRequest() {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("topic", "A model project topic");
        body.set("individualTopics", objectMapper.createArrayNode()
                .add(individualEntry(memberIds.get(0), "Model soil observations"))
                .add(individualEntry(memberIds.get(1), "Model watering comparison"))
                .add(individualEntry(memberIds.get(2), "Model result display")));
        body.put("initialSituation", "A model project needs a clearer starting point.");
        body.set("investigationConcerns", objectMapper.createArrayNode()
                .add(textEntry(memberIds.get(0), "How can soil be observed?"))
                .add(textEntry(memberIds.get(1), "How can watering be compared?"))
                .add(textEntry(memberIds.get(2), "How can results be displayed?")));
        body.put("objective", "Compare a set of model project outcomes.");
        body.set("plannedResults", objectMapper.createArrayNode()
                .add(textEntry(memberIds.get(0), "A model observation table."))
                .add(textEntry(memberIds.get(1), "A model comparison."))
                .add(textEntry(memberIds.get(2), "A model results display.")));
        body.set("milestones", objectMapper.createArrayNode()
                .add(milestone("Complete the model investigation", "2027-02-01")));
        body.put("cooperationPartner", "");
        return body;
    }

    private ObjectNode boundaryRequest() {
        ObjectNode body = validRequest();
        body.put("topic", "t".repeat(200));
        body.put("initialSituation", "s".repeat(400));
        body.put("objective", "o".repeat(400));
        for (int index = 0; index < memberIds.size(); index++) {
            ((ObjectNode) body.path("individualTopics").get(index)).put("topic", "i".repeat(200));
        }
        setTextLengths(body, "investigationConcerns", 800, 800, 800);
        setTextLengths(body, "plannedResults", 800, 800, 800);
        return body;
    }

    private void setTextLengths(ObjectNode body, String field, int first, int second, int third) {
        int[] lengths = {first, second, third};
        for (int index = 0; index < lengths.length; index++) {
            ((ObjectNode) body.path(field).get(index)).put("text", "x".repeat(lengths[index]));
        }
    }

    private void setEntryMember(ObjectNode body, String field, int index, String studentId) {
        ((ObjectNode) body.path(field).get(index)).put("studentId", studentId);
    }

    private ObjectNode individualEntry(String studentId, String topic) {
        return objectMapper.createObjectNode().put("studentId", studentId).put("topic", topic);
    }

    private ObjectNode textEntry(String studentId, String text) {
        return objectMapper.createObjectNode().put("studentId", studentId).put("text", text);
    }

    private ObjectNode milestone(String text, String date) {
        return objectMapper.createObjectNode().put("text", text).put("date", date);
    }

    private InvalidRequest invalid(String name, Consumer<ObjectNode> mutate) {
        return new InvalidRequest(name, mutate);
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record InvalidRequest(String name, Consumer<ObjectNode> mutate) {
    }

    private record RecordSnapshot(UUID id, String type, String ownerId, String parentId, String status, String payload,
            java.time.Instant createdAt) {
    }
}
