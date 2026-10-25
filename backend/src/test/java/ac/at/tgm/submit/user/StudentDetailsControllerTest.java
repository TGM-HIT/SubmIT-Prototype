package ac.at.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.data.StoredRecord;
import ac.at.tgm.submit.data.StoredRecordRepository;
import ac.at.tgm.submit.security.AuthenticatedUser;
import ac.at.tgm.submit.security.JwtService;
import ac.at.tgm.submit.security.Role;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:studentdetails;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class StudentDetailsControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private UserAccountRepository users;
    @Autowired private StudentClassRepository classes;
    @Autowired private ElectiveSubjectRepository subjects;
    @Autowired private JwtService jwt;
    @Autowired private StoredRecordRepository records;

    private UserAccount admin;
    private UserAccount teacher;
    private UserAccount student;
    private UserAccount secondStudent;

    @BeforeEach
    void setup() {
        users.deleteAll();
        subjects.deleteAll();
        classes.deleteAll();
        admin = user("admin", Role.ADMIN);
        teacher = user("teacher", Role.TEACHER);
        student = user("student", Role.STUDENT);
        secondStudent = user("second-student", Role.STUDENT);
        users.saveAll(List.of(admin, teacher, student, secondStudent));
    }

    @Test
    void administratorsMaintainSharedCataloguesAndAssignmentsSurviveReloadAndRenames() throws Exception {
        JsonNode classA = postJson("/api/student-classes", "{\"name\":\"  Class A  \"}", admin, 201);
        JsonNode classB = postJson("/api/student-classes", "{\"name\":\"Class B\"}", admin, 201);
        assertEquals("Class A", classA.path("name").asText());
        JsonNode subjectOne = postJson("/api/elective-subjects", "{\"name\":\"Subject 1\"}", admin, 201);
        List<JsonNode> created = new ArrayList<>();
        created.add(subjectOne);
        for (int i = 2; i <= 4; i++) created.add(postJson("/api/elective-subjects",
                "{\"name\":\"Subject " + i + "\"}", admin, 201));
        String four = "{\"classId\":\"" + classA.path("id").asText() + "\",\"electiveSubjectIds\":["
                + String.join(",", created.stream().map(item -> "\"" + item.path("id").asText() + "\"").toList()) + "]}";
        mvc.perform(put("/api/users/{id}/student-details", student.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(four))
                .andExpect(status().isOk()).andExpect(jsonPath("$.className").value("Class A"))
                .andExpect(jsonPath("$.electiveSubjectNames.length()").value(4));
        JsonNode loaded = getJson("/api/users/" + student.getId() + "/student-details", teacher);
        assertEquals("Class A", loaded.path("className").asText());
        assertEquals(4, loaded.path("electiveSubjectNames").size());
        assertEquals(4, loaded.path("electiveSubjectIds").size());
        mvc.perform(patch("/api/student-classes/{id}", classB.path("id").asText()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed Class B\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Renamed Class B"));
        mvc.perform(patch("/api/elective-subjects/{id}", subjectOne.path("id").asText()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Renamed Subject 1\"}"))
                .andExpect(status().isOk());
        loaded = getJson("/api/users/" + student.getId() + "/student-details", admin);
        assertEquals("Renamed Subject 1", loaded.path("electiveSubjectNames").get(0).asText());
        assertEquals(subjectOne.path("id").asText(), loaded.path("electiveSubjectIds").get(0).asText());
        assertEquals("Class A", loaded.path("className").asText());
        String one = "{\"classId\":\"" + classA.path("id").asText() + "\",\"electiveSubjectIds\":[\"" + subjectOne.path("id").asText() + "\"]}";
        mvc.perform(put("/api/users/{id}/student-details", secondStudent.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(one)).andExpect(status().isOk());
        JsonNode secondReload = getJson("/api/users/" + secondStudent.getId() + "/student-details", teacher);
        assertEquals(subjectOne.path("id").asText(), secondReload.path("electiveSubjectIds").get(0).asText());
        assertEquals("Renamed Subject 1", secondReload.path("electiveSubjectNames").get(0).asText());
        assertEquals(4, getJson("/api/users/" + student.getId() + "/student-details", teacher).path("electiveSubjectNames").size());
        String clear = "{\"classId\":\"" + classB.path("id").asText() + "\",\"electiveSubjectIds\":[]}";
        mvc.perform(put("/api/users/{id}/student-details", student.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(clear))
                .andExpect(status().isOk()).andExpect(jsonPath("$.className").value("Renamed Class B"))
                .andExpect(jsonPath("$.electiveSubjectNames.length()").value(0));
        loaded = getJson("/api/users/" + student.getId() + "/student-details", teacher);
        assertEquals("Renamed Class B", loaded.path("className").asText());
        assertEquals(0, loaded.path("electiveSubjectIds").size());
    }

    @Test
    void catalogueCreationReturnsCreatedWithEntryResponses() throws Exception {
        List<String> paths = List.of("/api/student-classes", "/api/elective-subjects");
        List<String> names = List.of("Class A", "Subject A");

        for (int i = 0; i < paths.size(); i++) {
            String path = paths.get(i);
            String name = names.get(i);
            JsonNode created = postJson(path, mapper.writeValueAsString(
                    java.util.Map.of("name", "  " + name + "  ")), admin, 201);
            String id = created.path("id").asText();
            assertFalse(id.isBlank(), path + " response must contain a non-empty ID");
            assertEquals(id, UUID.fromString(id).toString(), path + " ID must be a UUID");
            assertEquals(name, created.path("name").asText(), path + " response must contain the normalized name");

            JsonNode catalogue = getJson(path, teacher);
            assertEquals(1, catalogue.path("totalElements").asInt(), path + " should contain the created entry");
            boolean entryIsRetrievable = false;
            for (JsonNode item : catalogue.path("content")) {
                if (id.equals(item.path("id").asText()) && name.equals(item.path("name").asText())) {
                    entryIsRetrievable = true;
                }
            }
            assertTrue(entryIsRetrievable, path + " GET must return the created ID and normalized name");

            for (String invalidName : List.of("   ", "x".repeat(101))) {
                mvc.perform(post(path).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(java.util.Map.of("name", invalidName))))
                        .andExpect(status().isBadRequest());
            }
            for (String duplicateName : List.of(name, name.toUpperCase(java.util.Locale.ROOT))) {
                mvc.perform(post(path).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(java.util.Map.of("name", duplicateName))))
                        .andExpect(status().isConflict());
            }
            assertEquals(1, getJson(path, teacher).path("totalElements").asInt(),
                    path + " rejected requests must not create entries");

            mvc.perform(post(path).header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Teacher denied\"}"))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Anonymous denied\"}"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path).header("Authorization", "Bearer invalid").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Invalid token denied\"}"))
                    .andExpect(status().isUnauthorized());
            assertEquals(1, getJson(path, teacher).path("totalElements").asInt(),
                    path + " unauthorized requests must not create entries");
        }
    }

    @Test
    void catalogueNameBoundariesAndTrimmingAreEnforced() throws Exception {
        for (String path : List.of("/api/student-classes", "/api/elective-subjects")) {
            Map<String, String> persisted = new LinkedHashMap<>();
            String catalogueLabel = path.endsWith("classes") ? "Class" : "Subject";
            List<String> postNames = List.of("a", "B".repeat(100), "Trimmed Post " + catalogueLabel);

            for (int i = 0; i < postNames.size(); i++) {
                String expectedName = postNames.get(i);
                String requestName = i == 2 ? "  " + expectedName + "  " : expectedName;
                JsonNode created = postJson(path, mapper.writeValueAsString(Map.of("name", requestName)), admin, 201);
                String id = assertCatalogueEntry(created, expectedName, path + " POST response");
                persisted.put(id, expectedName);
                assertCatalogueContents(path, persisted);
            }

            for (String invalidName : List.of("", "   ", "x".repeat(101))) {
                mvc.perform(post(path).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of("name", invalidName))))
                        .andExpect(status().isBadRequest());
                assertCatalogueContents(path, persisted);
            }

            List<JsonNode> patchTargets = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                JsonNode seed = postJson(path, mapper.writeValueAsString(Map.of("name", "Patch target " + i)),
                        admin, 201);
                String id = assertCatalogueEntry(seed, "Patch target " + i, path + " PATCH seed response");
                persisted.put(id, "Patch target " + i);
                patchTargets.add(seed);
                assertCatalogueContents(path, persisted);
            }

            List<String> patchNames = List.of("z", "C".repeat(100), "Trimmed Patch " + catalogueLabel);
            for (int i = 0; i < patchTargets.size(); i++) {
                String id = patchTargets.get(i).path("id").asText();
                String expectedName = patchNames.get(i);
                String requestName = i == 2 ? "  " + expectedName + "  " : expectedName;
                JsonNode renamed = mapper.readTree(mvc.perform(patch(path + "/{id}", id)
                                .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of("name", requestName))))
                        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
                assertEquals(id, assertCatalogueEntry(renamed, expectedName, path + " PATCH response"));
                persisted.put(id, expectedName);
                assertCatalogueContents(path, persisted);
            }

            String unchangedId = patchTargets.get(0).path("id").asText();
            for (String invalidName : List.of("", "   ", "x".repeat(101))) {
                mvc.perform(patch(path + "/{id}", unchangedId).header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of("name", invalidName))))
                        .andExpect(status().isBadRequest());
                assertCatalogueContents(path, persisted);
            }
        }
    }

    @Test
    void catalogueNamesAreUniqueIgnoringCase() throws Exception {
        for (String path : List.of("/api/student-classes", "/api/elective-subjects")) {
            Map<String, String> persisted = new LinkedHashMap<>();
            JsonNode alpha = postJson(path, "{\"name\":\"Alpha\"}", admin, 201);
            JsonNode beta = postJson(path, "{\"name\":\"Beta\"}", admin, 201);
            String alphaId = assertCatalogueEntry(alpha, "Alpha", path + " first POST response");
            String betaId = assertCatalogueEntry(beta, "Beta", path + " second POST response");
            persisted.put(alphaId, "Alpha");
            persisted.put(betaId, "Beta");
            assertCatalogueContents(path, persisted);

            for (String duplicateName : List.of("Alpha", "  aLpHa  ")) {
                mvc.perform(post(path).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of("name", duplicateName))))
                        .andExpect(status().isConflict());
                assertCatalogueContents(path, persisted);
            }

            for (String conflictingName : List.of("Alpha", "  aLpHa  ")) {
                mvc.perform(patch(path + "/{id}", betaId).header("Authorization", bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(Map.of("name", conflictingName))))
                        .andExpect(status().isConflict());
                assertCatalogueContents(path, persisted);
            }

            JsonNode unchanged = mapper.readTree(mvc.perform(patch(path + "/{id}", alphaId)
                            .header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"Alpha\"}"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(alphaId, assertCatalogueEntry(unchanged, "Alpha", path + " unchanged-name PATCH response"));
            assertCatalogueContents(path, persisted);
        }
    }

    @Test
    void rejectsInvalidAssignmentsWithoutPartialChangesAndProtectsCatalogues() throws Exception {
        var entry = classes.save(new StudentClassEntity(UUID.randomUUID(), "Class"));
        List<ElectiveSubjectEntity> entries = new ArrayList<>();
        for (int i = 0; i < 5; i++) entries.add(subjects.save(new ElectiveSubjectEntity(UUID.randomUUID(), "Subject " + i)));
        String valid = "{\"classId\":\"" + entry.getId() + "\",\"electiveSubjectIds\":[]}";
        mvc.perform(put("/api/users/{id}/student-details", student.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isOk());
        String tooMany = "{\"classId\":\"" + entry.getId() + "\",\"electiveSubjectIds\":[" + String.join(",", entries.stream().map(value -> "\"" + value.getId() + "\"").toList()) + "]}";
        String duplicate = "{\"classId\":\"" + entry.getId() + "\",\"electiveSubjectIds\":[\"" + entries.get(0).getId() + "\",\"" + entries.get(0).getId() + "\"]}";
        String unknown = "{\"classId\":\"" + entry.getId() + "\",\"electiveSubjectIds\":[\"" + UUID.randomUUID() + "\"]}";
        String unknownClass = "{\"classId\":\"" + UUID.randomUUID() + "\",\"electiveSubjectIds\":[]}";
        for (String body : List.of(tooMany, duplicate, unknown, unknownClass)) {
            mvc.perform(put("/api/users/{id}/student-details", student.getId()).header("Authorization", bearer(admin))
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
            JsonNode unchanged = getJson("/api/users/" + student.getId() + "/student-details", teacher);
            assertEquals("Class", unchanged.path("className").asText());
            assertEquals(0, unchanged.path("electiveSubjectIds").size());
        }
        mvc.perform(put("/api/users/{id}/student-details", teacher.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/student-classes").header("Authorization", bearer(teacher)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Denied\"}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/users/{id}/student-details", student.getId()).header("Authorization", bearer(teacher))
                        .contentType(MediaType.APPLICATION_JSON).content(valid)).andExpect(status().isForbidden());
        mvc.perform(get("/api/student-classes").header("Authorization", bearer(student))).andExpect(status().isForbidden());
        mvc.perform(get("/api/student-classes").header("Authorization", bearer(teacher))).andExpect(status().isOk());
        mvc.perform(get("/api/elective-subjects").header("Authorization", bearer(teacher))).andExpect(status().isOk());
        mvc.perform(get("/api/users/{id}/student-details", student.getId()).header("Authorization", bearer(student)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/student-classes")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/elective-subjects").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Denied\"}")).andExpect(status().isUnauthorized());
        assertTrue(subjects.count() == 5);
    }

    @Test
    void catalogueCollectionsDefaultToTwentyAndClampLargeSizes() throws Exception {
        List<StudentClassEntity> classEntries = new ArrayList<>();
        List<ElectiveSubjectEntity> subjectEntries = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            classEntries.add(new StudentClassEntity(UUID.randomUUID(), "Class " + i));
            subjectEntries.add(new ElectiveSubjectEntity(UUID.randomUUID(), "Subject " + i));
        }
        classes.saveAll(classEntries);
        subjects.saveAll(subjectEntries);

        for (String path : List.of("/api/student-classes", "/api/elective-subjects")) {
            JsonNode defaultPage = mapper.readTree(mvc.perform(get(path).header("Authorization", bearer(teacher)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertTrue(defaultPage.has("page") && defaultPage.get("page").isNumber(), path + " default page is numeric");
            assertEquals(0, defaultPage.path("page").asInt(), path + " default page");
            assertEquals(20, defaultPage.path("size").asInt(), path + " default size");
            assertEquals(105, defaultPage.path("totalElements").asInt(), path + " default totalElements");
            assertEquals(6, defaultPage.path("totalPages").asInt(), path + " default totalPages");
            assertEquals(20, defaultPage.path("content").size(), path + " default content size");

            JsonNode oversizedPage = mapper.readTree(mvc.perform(get(path).param("size", "101")
                            .header("Authorization", bearer(teacher)))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertTrue(oversizedPage.has("page") && oversizedPage.get("page").isNumber(),
                    path + " oversized page is numeric");
            assertEquals(0, oversizedPage.path("page").asInt(), path + " oversized page");
            assertEquals(100, oversizedPage.path("size").asInt(), path + " oversized size");
            assertEquals(105, oversizedPage.path("totalElements").asInt(), path + " oversized totalElements");
            assertEquals(2, oversizedPage.path("totalPages").asInt(), path + " oversized totalPages");
            assertEquals(100, oversizedPage.path("content").size(), path + " oversized content size");
        }
    }


    @Test
    void registrationAndRoleConversionReceiveDefaultClassAndRemainImmediatelyUsable() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-student\",\"password\":\"password-long-enough\",\"displayName\":\"New Student\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("STUDENT"));
        UserAccount registered = users.findByUsername("new-student").orElseThrow();
        assertEquals("No class / unassigned", getJson("/api/users/" + registered.getId() + "/student-details", admin)
                .path("className").asText());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-student\",\"password\":\"password-long-enough\"}"))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/users/{id}/role", teacher.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"STUDENT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("STUDENT"));
        assertNoAutomaticEnrollment(teacher.getId());
        assertEquals("No class / unassigned", getJson("/api/users/" + teacher.getId() + "/student-details", admin)
                .path("className").asText());
        var assignedClass = classes.save(new StudentClassEntity(UUID.randomUUID(), "Assigned"));
        mvc.perform(put("/api/users/{id}/student-details", teacher.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"classId\":\"" + assignedClass.getId()
                                + "\",\"electiveSubjectIds\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.classId").value(assignedClass.getId().toString()));
    }

    private void assertNoAutomaticEnrollment(UUID studentId) {
        String id = studentId.toString();
        List<StoredRecord> allRecords = records.findAll();
        assertFalse(allRecords.stream()
                        .anyMatch(record -> "student_application".equals(record.getType())
                                && id.equals(record.getOwnerId())),
                "Changing a teacher to STUDENT must not create a solo application.");
        assertFalse(allRecords.stream()
                        .anyMatch(record -> "project_group".equals(record.getType())
                                && record.getPayload().contains(id)),
                "Changing a teacher to STUDENT must not add the account to a group.");
        assertFalse(allRecords.stream()
                        .anyMatch(record -> "allocation_assignment".equals(record.getType())
                                && (id.equals(record.getOwnerId()) || record.getPayload().contains(id))),
                "Changing a teacher to STUDENT must not create an allocation assignment.");
        assertEquals(Role.STUDENT, users.findById(teacher.getId()).orElseThrow().getRole());
    }

    private JsonNode postJson(String path, String body, UserAccount caller, int expectedStatus) throws Exception {
        var result = mvc.perform(post(path).header("Authorization", bearer(caller)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus)).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getJson(String path, UserAccount caller) throws Exception {
        return mapper.readTree(mvc.perform(get(path).header("Authorization", bearer(caller)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String assertCatalogueEntry(JsonNode entry, String expectedName, String context) {
        assertTrue(entry.isObject(), context + " must be a JSON object");
        assertTrue(entry.has("id") && entry.get("id").isTextual(), context + " must contain a string ID");
        assertTrue(entry.has("name") && entry.get("name").isTextual(), context + " must contain a string name");
        String id = entry.get("id").textValue();
        assertEquals(id, UUID.fromString(id).toString(), context + " ID must be a UUID");
        assertEquals(expectedName, entry.get("name").textValue(), context + " must contain the normalized name");
        return id;
    }

    private void assertCatalogueContents(String path, Map<String, String> expected) throws Exception {
        JsonNode catalogue = getJson(path, teacher);
        assertTrue(catalogue.has("totalElements") && catalogue.get("totalElements").isIntegralNumber(),
                path + " GET must contain a numeric totalElements");
        assertTrue(catalogue.has("content") && catalogue.get("content").isArray(),
                path + " GET must contain an entries array");
        assertEquals(expected.size(), catalogue.get("totalElements").intValue(),
                path + " GET must return the persisted entry count");
        Map<String, String> actual = new LinkedHashMap<>();
        for (JsonNode item : catalogue.get("content")) {
            String id = assertCatalogueEntry(item, item.has("name") && item.get("name").isTextual()
                    ? item.get("name").textValue() : "", path + " GET entry");
            actual.put(id, item.get("name").textValue());
        }
        assertEquals(expected, actual, path + " GET must return all persisted IDs and names");
    }

    private UserAccount user(String name, Role role) {
        return new UserAccount(UUID.randomUUID(), name, name, "unused", role);
    }

    private String bearer(UserAccount user) {
        return "Bearer " + jwt.issueToken(new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole()));
    }
}
