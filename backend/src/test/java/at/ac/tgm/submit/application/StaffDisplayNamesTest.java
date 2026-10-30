package at.ac.tgm.submit.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.user.UserAccount;
import at.ac.tgm.submit.user.UserAccountRepository;
import at.ac.tgm.submit.user.StudentClassEntity;
import at.ac.tgm.submit.user.StudentClassRepository;
import at.ac.tgm.submit.user.ElectiveSubjectEntity;
import at.ac.tgm.submit.user.ElectiveSubjectRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:bug09display;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class StaffDisplayNamesTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecordStore store;
    @Autowired private UserAccountRepository users;
    @Autowired private StudentClassRepository classes;
    @Autowired private ElectiveSubjectRepository subjects;

    private UserAccount firstStudent;
    private UserAccount secondStudent;
    private StoredRecord firstProject;
    private StoredRecord secondProject;
    private StoredRecord period;
    private StoredRecord run;
    private String adminToken;
    private String teacherToken;
    private String studentToken;
    private String assignedClassName;
    private String assignedSubjectName;

    @BeforeEach
    void setUp() throws Exception {
        firstStudent = name("student01", "Alex Example");
        secondStudent = name("student02", "Blair Example");
        adminToken = login("admin01");
        teacherToken = login("teacher01");
        studentToken = login("student01");
        assignedClassName = "Class " + UUID.randomUUID();
        assignedSubjectName = "Subject " + UUID.randomUUID();
        StudentClassEntity studentClass = classes.save(new StudentClassEntity(UUID.randomUUID(), assignedClassName));
        ElectiveSubjectEntity elective = subjects.save(new ElectiveSubjectEntity(UUID.randomUUID(), assignedSubjectName));
        firstStudent.setStudentClass(studentClass);
        firstStudent.setElectiveSubjects(List.of(elective));
        users.save(firstStudent);
        firstProject = idea("Solar design project");
        secondProject = idea("Water quality project");
        ObjectNode periodData = store.object().put("label", "Staff display period")
                .put("schoolYear", "2026/2027")
                .put("opensAt", "2026-10-10T09:00:00Z")
                .put("closesAt", "2026-10-20T17:00:00Z");
        ArrayNode catalogue = periodData.putArray("projectIds");
        catalogue.add(firstProject.getId().toString()).add(secondProject.getId().toString());
        period = store.create("application_period", null, null, "ALLOCATED", periodData);
        createApplication(firstStudent, firstProject);
        createApplication(secondStudent, secondProject);
        run = createRun();
    }

    @Test
    void authorizedApplicationSummaryReturnsIdsAndCurrentNamesAndTitles() throws Exception {
        assertApplicationNames(adminToken, "Alex Example", "Blair Example", "Solar design project");
        assertApplicationNames(teacherToken, "Alex Example", "Blair Example", "Solar design project");

        firstStudent.setDisplayName("Alex Renamed");
        users.save(firstStudent);
        updateIdea(firstProject, "Solar project renamed");
        assertApplicationNames(adminToken, "Alex Renamed", "Blair Example", "Solar project renamed");
        assertApplicationNames(teacherToken, "Alex Renamed", "Blair Example", "Solar project renamed");
    }

    @Test
    void authorizedAllocationResultReturnsCurrentNamesAndTitlesWithoutChangingSnapshot() throws Exception {
        String originalPayload = run.getPayload();
        for (String token : List.of(adminToken, teacherToken)) {
            JsonNode response = getJson("/api/allocation-runs/" + run.getId(), token);
            JsonNode result = response.path("result");
            JsonNode assigned = result.path("assignments").get(0);
            assertEquals(firstStudent.getId().toString(), assigned.path("studentId").asText());
            assertEquals(firstProject.getId().toString(), assigned.path("projectId").asText());
            assertEquals("Alex Example", assigned.path("studentDisplayName").asText());
            assertEquals(assignedClassName, assigned.path("className").asText());
            assertEquals(assignedSubjectName, assigned.path("electiveSubjectNames").get(0).asText());
            assertEquals("Solar design project", assigned.path("projectTitle").asText());
            assertEquals(secondStudent.getId().toString(), result.path("unassignedStudents").get(0)
                    .path("studentId").asText());
            assertEquals("Blair Example", result.path("unassignedStudents").get(0)
                    .path("studentDisplayName").asText());
            assertEquals(secondProject.getId().toString(), result.path("unassignedProjects").get(0)
                    .path("projectId").asText());
            assertEquals("Water quality project", result.path("unassignedProjects").get(0)
                    .path("projectTitle").asText());
            assertEquals("RANKED_PROJECTS_NOT_FORMED", result.path("unassignedStudents").get(0)
                    .path("reason").asText());
            assertEquals("NOT_SELECTED_BY_OPTIMIZATION", result.path("unassignedProjects").get(0)
                    .path("reason").asText());
        }

        firstStudent.setDisplayName("Alex New Name");
        users.save(firstStudent);
        updateIdea(firstProject, "Solar current title");
        for (String token : List.of(adminToken, teacherToken)) {
            JsonNode refreshed = getJson("/api/allocation-runs/" + run.getId(), token).path("result");
            assertEquals("Alex New Name", refreshed.path("assignments").get(0)
                    .path("studentDisplayName").asText());
            assertEquals("Solar current title", refreshed.path("assignments").get(0).path("projectTitle").asText());
        }
        String persistedPayload = store.repository().findById(run.getId()).orElseThrow().getPayload();
        assertEquals(originalPayload, persistedPayload);
        assertFalse(persistedPayload.contains("studentDisplayName"));
        assertFalse(persistedPayload.contains("projectTitle"));
    }

    @Test
    void ideaAndStudentOwnedResponsesRespectStaffOnlyAssignmentPrivacy() throws Exception {
        for (String token : List.of(adminToken, teacherToken)) {
            JsonNode ideaResponse = getJson("/api/ideas/" + firstProject.getId(), token);
            JsonNode owners = requiredArray(ideaResponse, "owners");
            assertTrue(owners.size() > 0, "staff idea response should contain its owner");
            JsonNode owner = requiredObject(owners.get(0), "staff idea owner");
            assertEquals(assignedClassName, requiredText(owner, "className"));
            JsonNode ownerSubjects = requiredArray(owner, "electiveSubjectNames");
            assertTrue(ownerSubjects.size() > 0, "staff owner should have an assigned elective subject");
            assertTrue(ownerSubjects.get(0).isTextual());
            assertEquals(assignedSubjectName, ownerSubjects.get(0).textValue());
        }
        JsonNode studentIdea = getJson("/api/ideas/" + firstProject.getId(), studentToken);
        JsonNode studentOwners = requiredArray(studentIdea, "owners");
        assertTrue(studentOwners.size() > 0, "student idea response should contain its owner");
        JsonNode studentOwner = requiredObject(studentOwners.get(0), "student idea owner");
        assertFalse(studentOwner.has("className"));
        assertFalse(studentOwner.has("electiveSubjectNames"));
        JsonNode ownApplication = getJson("/api/application-periods/" + period.getId() + "/applications/me", studentToken);
        assertTrue(ownApplication.isObject());
        assertFalse(ownApplication.has("className"));
        assertFalse(ownApplication.has("electiveSubjectNames"));
        JsonNode firstPage = getJson("/api/users?role=STUDENT&page=0&size=20", adminToken);
        assertAccountPage(firstPage, 0, 20);
        assertTrue(requiredLong(firstPage, "totalElements") > 20,
                "the unfiltered account list should contain more than one page of students");

        JsonNode selectedAccounts = getJson("/api/users?role=STUDENT&q=student01&page=0&size=20", adminToken);
        assertAccountPage(selectedAccounts, 0, 20);
        assertEquals(1, requiredLong(selectedAccounts, "totalElements"));
        assertEquals(1, requiredArray(selectedAccounts, "content").size());
        JsonNode accountRow = exactAccount(selectedAccounts, firstStudent.getId());
        assertEquals(assignedClassName, requiredText(accountRow, "className"));
        JsonNode electiveSubjects = requiredArray(accountRow, "electiveSubjectNames");
        assertTrue(electiveSubjects.size() > 0);
        assertTrue(electiveSubjects.get(0).isTextual());
        assertEquals(assignedSubjectName, electiveSubjects.get(0).textValue());
    }

    @Test
    void assignmentPrivacySelectionFindsTargetBeyondFirstAccountPage() throws Exception {
        JsonNode allStudents = getJson("/api/users?role=STUDENT&page=0&size=100", adminToken);
        assertAccountPage(allStudents, 0, 100);
        JsonNode allStudentRows = requiredArray(allStudents, "content");
        long totalStudents = requiredLong(allStudents, "totalElements");
        assertTrue(totalStudents > 20, "fixture must contain more than one page of students");
        assertTrue(allStudentRows.size() > 20, "the account response must expose the sorted target candidates");

        JsonNode targetCandidate = requiredObject(allStudentRows.get(20), "student beyond first page");
        UUID targetId = UUID.fromString(requiredText(targetCandidate, "id"));
        String targetUsername = requiredText(targetCandidate, "username");

        JsonNode firstPage = getJson("/api/users?role=STUDENT&page=0&size=20", adminToken);
        assertAccountPage(firstPage, 0, 20);
        assertEquals(totalStudents, requiredLong(firstPage, "totalElements"));
        assertEquals(20, requiredArray(firstPage, "content").size());
        assertEquals(accountIds(firstPage), accountIds(allStudents).subList(0, 20),
                "page zero should contain the first 20 accounts in the observed ID sort");
        assertFalse(accountIds(firstPage).contains(targetId.toString()),
                "chosen target must be outside page zero: " + targetId);

        UserAccount persistedTarget = users.findById(targetId).orElseThrow();
        assertEquals(targetUsername, persistedTarget.getUsername());
        String targetClassName = "Boundary class " + UUID.randomUUID();
        String targetSubjectName = "Boundary subject " + UUID.randomUUID();
        StudentClassEntity targetClass = classes.save(
                new StudentClassEntity(UUID.randomUUID(), targetClassName));
        ElectiveSubjectEntity targetSubject = subjects.save(
                new ElectiveSubjectEntity(UUID.randomUUID(), targetSubjectName));
        persistedTarget.setStudentClass(targetClass);
        persistedTarget.setElectiveSubjects(List.of(targetSubject));
        users.save(persistedTarget);

        JsonNode filteredTarget = getJson("/api/users?role=STUDENT&q=" + targetUsername + "&page=0&size=20",
                adminToken);
        assertAccountPage(filteredTarget, 0, 20);
        assertEquals(1, requiredLong(filteredTarget, "totalElements"));
        JsonNode filteredRows = requiredArray(filteredTarget, "content");
        assertEquals(1, filteredRows.size(), "username filter must select exactly one account");
        JsonNode account = exactAccount(filteredTarget, targetId);
        assertEquals(targetClassName, requiredText(account, "className"));
        JsonNode assignedSubjects = requiredArray(account, "electiveSubjectNames");
        assertEquals(1, assignedSubjects.size());
        assertTrue(assignedSubjects.get(0).isTextual());
        assertEquals(targetSubjectName, assignedSubjects.get(0).textValue());
    }

    @Test
    void unresolvedApplicationAndAllocationReferencesKeepIdsAndReturnNeutralLabels() throws Exception {
        UUID missingStudentId = UUID.randomUUID();
        UUID missingProjectId = UUID.randomUUID();
        ObjectNode applicationPayload = store.object();
        applicationPayload.putArray("preferences").addObject()
                .put("projectId", missingProjectId.toString()).put("rank", 1);
        StoredRecord application = store.create("student_application", missingStudentId.toString(),
                period.getId().toString(), "SUBMITTED", applicationPayload);

        JsonNode applications = getJson("/api/application-periods/" + period.getId() + "/applications", teacherToken);
        JsonNode unresolvedApplication = null;
        for (JsonNode entry : applications.path("content")) {
            if (application.getId().toString().equals(entry.path("id").asText())) unresolvedApplication = entry;
        }
        assertTrue(unresolvedApplication != null, "unresolved application should remain in the staff summary");
        assertEquals(missingStudentId.toString(), unresolvedApplication.path("studentId").asText());
        assertEquals("Unknown student", unresolvedApplication.path("studentDisplayName").asText());
        assertEquals(missingProjectId.toString(),
                unresolvedApplication.path("preferences").get(0).path("projectId").asText());
        assertEquals("Unknown project", unresolvedApplication.path("preferences").get(0)
                .path("projectTitle").asText());

        ObjectNode runPayload = store.object().put("createdAt", "2026-10-10T10:00:00Z");
        ObjectNode result = runPayload.putObject("result");
        result.putArray("assignments").addObject()
                .put("studentId", missingStudentId.toString()).put("projectId", missingProjectId.toString());
        result.putArray("unassignedStudents").addObject().put("studentId", missingStudentId.toString())
                .put("reason", "RANKED_PROJECTS_NOT_FORMED");
        result.putArray("unassignedProjects").addObject().put("projectId", missingProjectId.toString())
                .put("reason", "NOT_SELECTED_BY_OPTIMIZATION");
        StoredRecord unresolvedRun = store.create("allocation_run", null, period.getId().toString(),
                "COMPLETED", runPayload);

        JsonNode unresolvedResult = getJson("/api/allocation-runs/" + unresolvedRun.getId(), teacherToken)
                .path("result");
        JsonNode assignment = unresolvedResult.path("assignments").get(0);
        assertEquals(missingStudentId.toString(), assignment.path("studentId").asText());
        assertEquals("Unknown student", assignment.path("studentDisplayName").asText());
        assertEquals(missingProjectId.toString(), assignment.path("projectId").asText());
        assertEquals("Unknown project", assignment.path("projectTitle").asText());
        JsonNode unassignedStudent = unresolvedResult.path("unassignedStudents").get(0);
        assertEquals(missingStudentId.toString(), unassignedStudent.path("studentId").asText());
        assertEquals("Unknown student", unassignedStudent.path("studentDisplayName").asText());
        JsonNode unassignedProject = unresolvedResult.path("unassignedProjects").get(0);
        assertEquals(missingProjectId.toString(), unassignedProject.path("projectId").asText());
        assertEquals("Unknown project", unassignedProject.path("projectTitle").asText());
    }

    @Test
    void studentsCannotReadStaffNameResponses() throws Exception {
        mockMvc.perform(get("/api/application-periods/{id}/applications", period.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/allocation-runs/{id}", run.getId())
                        .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/application-periods/{id}/applications", period.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/allocation-runs/{id}", run.getId()))
                .andExpect(status().isUnauthorized());
    }

    private void assertAccountPage(JsonNode response, int expectedPage, int expectedSize) {
        assertTrue(response.isObject(), "account page should be a JSON object");
        requiredArray(response, "content");
        assertEquals(expectedPage, requiredInt(response, "page"));
        assertEquals(expectedSize, requiredInt(response, "size"));
        long totalElements = requiredLong(response, "totalElements");
        int expectedPages = (int) ((totalElements + expectedSize - 1) / expectedSize);
        assertEquals(expectedPages, requiredInt(response, "totalPages"));
    }

    private JsonNode exactAccount(JsonNode response, UUID expectedId) {
        JsonNode found = null;
        int matches = 0;
        for (JsonNode row : requiredArray(response, "content")) {
            requiredObject(row, "account row");
            if (expectedId.toString().equals(requiredText(row, "id"))) {
                found = row;
                matches++;
            }
        }
        assertEquals(1, matches, "expected exactly account " + expectedId
                + " in returned IDs " + accountIds(response));
        return found;
    }

    private List<String> accountIds(JsonNode response) {
        return requiredArray(response, "content").valueStream()
                .map(row -> requiredText(requiredObject(row, "account row"), "id")).toList();
    }

    private JsonNode requiredObject(JsonNode node, String description) {
        assertTrue(node != null && node.isObject(), description + " should be a JSON object");
        return node;
    }

    private JsonNode requiredArray(JsonNode parent, String field) {
        assertTrue(parent != null && parent.isObject() && parent.has(field) && parent.get(field).isArray(),
                field + " should be a present JSON array");
        return parent.get(field);
    }

    private String requiredText(JsonNode parent, String field) {
        assertTrue(parent != null && parent.isObject() && parent.has(field) && parent.get(field).isTextual(),
                field + " should be a present JSON string");
        return parent.get(field).textValue();
    }

    private int requiredInt(JsonNode parent, String field) {
        assertTrue(parent != null && parent.isObject() && parent.has(field) && parent.get(field).isIntegralNumber()
                        && parent.get(field).canConvertToInt(),
                field + " should be a present JSON integer");
        return parent.get(field).intValue();
    }

    private long requiredLong(JsonNode parent, String field) {
        assertTrue(parent != null && parent.isObject() && parent.has(field) && parent.get(field).isIntegralNumber()
                        && parent.get(field).canConvertToLong(),
                field + " should be a present JSON integer");
        return parent.get(field).longValue();
    }

    private void assertApplicationNames(String token, String firstName, String secondName, String firstTitle)
            throws Exception {
        JsonNode response = getJson("/api/application-periods/" + period.getId() + "/applications", token);
        assertEquals(2, response.path("content").size());
        JsonNode first = byStudent(response, firstStudent.getId());
        JsonNode second = byStudent(response, secondStudent.getId());
        assertEquals(firstStudent.getId().toString(), first.path("studentId").asText());
        assertEquals(firstName, first.path("studentDisplayName").asText());
        assertEquals(assignedClassName, first.path("className").asText());
        assertEquals(assignedSubjectName, first.path("electiveSubjectNames").get(0).asText());
        assertEquals(secondName, second.path("studentDisplayName").asText());
        assertEquals(firstProject.getId().toString(), first.path("preferences").get(0).path("projectId").asText());
        assertEquals(firstTitle, first.path("preferences").get(0).path("projectTitle").asText());
        assertEquals("Water quality project", second.path("preferences").get(0).path("projectTitle").asText());
        assertTrue(first.path("id").isTextual());
    }

    private JsonNode byStudent(JsonNode response, UUID studentId) {
        for (JsonNode application : response.path("content")) {
            if (studentId.toString().equals(application.path("studentId").asText())) return application;
        }
        throw new AssertionError("application for student was not returned");
    }

    private JsonNode getJson(String path, String token) throws Exception {
        MvcResult result = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", "testing-password"))))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private UserAccount name(String username, String displayName) {
        UserAccount user = users.findByUsername(username).orElseThrow();
        user.setDisplayName(displayName);
        return users.save(user);
    }

    private StoredRecord idea(String title) {
        return store.create("idea", firstStudent.getId().toString(), null, "ACCEPTED_FOR_APPLICATIONS",
                store.object().put("title", title).put("schoolYear", "2026/2027"));
    }

    private void updateIdea(StoredRecord idea, String title) {
        store.update(idea, idea.getStatus(), store.object().put("title", title).put("schoolYear", "2026/2027"));
    }

    private void createApplication(UserAccount student, StoredRecord project) {
        ObjectNode payload = store.object();
        payload.putArray("preferences").addObject().put("projectId", project.getId().toString()).put("rank", 1);
        store.create("student_application", student.getId().toString(), period.getId().toString(), "SUBMITTED", payload);
    }

    private StoredRecord createRun() {
        ObjectNode payload = store.object().put("createdAt", "2026-10-10T10:00:00Z");
        ObjectNode result = payload.putObject("result");
        result.putArray("assignments").addObject().put("studentId", firstStudent.getId().toString())
                .put("projectId", firstProject.getId().toString()).put("preferenceRank", 1);
        result.putArray("unassignedStudents").addObject().put("studentId", secondStudent.getId().toString())
                .put("reason", "RANKED_PROJECTS_NOT_FORMED").putArray("rankedProjectIds");
        result.putArray("unassignedProjects").addObject().put("projectId", secondProject.getId().toString())
                .put("reason", "NOT_SELECTED_BY_OPTIMIZATION").put("interestedStudents", 0);
        return store.create("allocation_run", null, period.getId().toString(), "COMPLETED", payload);
    }
}
