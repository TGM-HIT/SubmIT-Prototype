package at.ac.tgm.submit.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import at.ac.tgm.submit.data.RecordStore;
import at.ac.tgm.submit.data.StoredRecord;
import at.ac.tgm.submit.security.Role;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:testingtgmimportflow;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "DADB_TESTING_MINIMAL_FIXTURES=true"
})
@ActiveProfiles("testing")
@AutoConfigureMockMvc
@Transactional
class TestingTgmImportFlowTest {
    private static final String PASSWORD = "testing-password";
    private static final Path INVENTED_PATH = Path.of("invented-tgm-import.xlsx");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private Environment environment;
    @Autowired private TgmWorkbookReader reader;
    @Autowired private TgmWorkbookImportPlanner planner;
    @Autowired private TgmWorkbookImportWriter writer;
    @Autowired private UserAccountRepository users;
    @Autowired private StudentClassRepository classes;
    @Autowired private PasswordEncoder passwords;
    @Autowired private RecordStore store;

    @Test
    void importsAccountsCataloguePeriodAndReciprocalGroupsThroughExistingLifecycle() throws Exception {
        byte[] source = TgmWorkbookReaderTest.importScenarioWorkbook();
        TestingTgmWorkbookImporter importer = importer();
        Instant importStartedAt = Instant.now();
        importer.importSource(INVENTED_PATH, source);
        Instant importFinishedAt = Instant.now();

        assertEquals(9, users.findAll().stream()
                .filter(user -> user.getStudentClass() != null
                        && TgmWorkbookReader.SHEETS.contains(user.getStudentClass().getName()))
                .count());
        UserAccount solo = users.findByUsername("SampleSolo").orElseThrow();
        assertEquals("Sample Solo", solo.getDisplayName());
        assertEquals("4aHIT", solo.getStudentClass().getName());
        assertEquals(List.of(Role.STUDENT), solo.getRoles());
        assertTrue(passwords.matches(PASSWORD, solo.getPasswordHash()));
        assertTrue(TgmWorkbookReader.SHEETS.stream().allMatch(name -> classes.findByName(name).isPresent()));
        assertFalse(users.findByUsername("student01").isEmpty());

        StoredRecord period = tgmPeriod();
        ObjectNode periodData = store.parse(period);
        assertEquals("Diplomarbeiten 2026/27", periodData.path("label").asText());
        assertEquals("2026/2027", periodData.path("schoolYear").asText());
        assertEquals("OPEN", period.getStatus());
        assertEquals("SELECTED", periodData.path("eligibilityMode").asText());
        assertEquals(4, periodData.path("eligibleClassIds").size());
        assertEquals(3, periodData.path("projectIds").size());

        List<StoredRecord> projects = store.repository()
                .findByTypeAndStatus("idea", "ACCEPTED_FOR_APPLICATIONS", PageRequest.of(0, 100)).getContent()
                .stream().filter(idea -> periodData.path("projectIds").toString().contains(idea.getId().toString()))
                .toList();
        assertEquals(Set.of("Invented Cedar", "Invented Birch", "Invented Maple"), projects.stream()
                .map(idea -> store.parse(idea).path("title").asText()).collect(Collectors.toSet()));
        assertEquals(3, projects.size());
        for (StoredRecord project : projects) {
            assertEquals("teacher01", users.findById(UUID.fromString(project.getOwnerId())).orElseThrow().getUsername());
            assertTrue(store.parse(project).path("description").asText().contains("Local TGM testing content"));
            StoredRecord specification = store.repository()
                    .findFirstByTypeAndParentIdOrderByCreatedAtDesc("specification", project.getId().toString())
                    .orElseThrow();
            assertEquals("ACCEPTED_FOR_APPLICATIONS", specification.getStatus());
            assertTrue(store.parse(specification).path("scope").asText().contains("Local TGM testing content"));
        }

        String soloToken = login("SampleSolo");
        JsonNode soloApplication = json(mockMvc.perform(get("/api/application-periods/{id}/applications/me",
                        period.getId()).header("Authorization", "Bearer " + soloToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("SUBMITTED", soloApplication.path("status").asText());
        assertEquals(List.of("Invented Cedar", "Invented Birch", "Invented Maple"),
                soloApplication.path("preferences").findValuesAsText("projectTitle"));

        String teacherToken = login("teacher01");
        JsonNode periodPage = json(mockMvc.perform(get("/api/application-periods")
                        .header("Authorization", "Bearer " + teacherToken).param("page", "0").param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, periodPage.path("totalElements").asInt());
        assertEquals(period.getId().toString(), periodPage.path("content").get(0).path("id").asText());
        JsonNode publicPeriod = periodPage.path("content").get(0);
        assertTrue(publicPeriod.has("opensAt") && publicPeriod.path("opensAt").isTextual());
        assertTrue(publicPeriod.has("closesAt") && publicPeriod.path("closesAt").isTextual());
        Instant opensAt = Instant.parse(publicPeriod.path("opensAt").textValue());
        Instant closesAt = Instant.parse(publicPeriod.path("closesAt").textValue());
        assertTrue(!opensAt.isBefore(importStartedAt.minusSeconds(60)),
                "period opening predates the import-start bracket");
        assertTrue(!opensAt.isAfter(importFinishedAt.minusSeconds(60)),
                "period opening exceeds the import-finish bracket");
        assertTrue(!closesAt.isBefore(importStartedAt.plusSeconds(7 * 24 * 60 * 60)),
                "period closing predates the import-start bracket");
        assertTrue(!closesAt.isAfter(importFinishedAt.plusSeconds(7 * 24 * 60 * 60)),
                "period closing exceeds the import-finish bracket");
        assertEquals(604_860, Duration.between(opensAt, closesAt).getSeconds());
        assertEquals(0, Duration.between(opensAt, closesAt).toNanosPart());
        JsonNode groupPage = json(mockMvc.perform(get("/api/application-periods/{id}/applying-groups",
                        period.getId()).header("Authorization", "Bearer " + teacherToken)
                        .param("page", "0").param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(2, groupPage.path("totalElements").asInt());
        Set<Integer> memberCounts = new java.util.HashSet<>();
        for (JsonNode group : groupPage.path("content")) {
            assertEquals("SUBMITTED", group.path("status").asText());
            JsonNode members = group.path("members");
            assertTrue(members.isArray());
            assertTrue(Set.of(2, 5).contains(members.size()), "unexpected submitted group size");
            memberCounts.add(members.size());
            for (JsonNode member : members) {
                assertTrue(member.isObject());
                assertTrue(member.has("approved"));
                assertTrue(member.path("approved").isBoolean());
                assertTrue(member.path("approved").booleanValue());
            }
            assertEquals(List.of("Invented Cedar", "Invented Birch", "Invented Maple"),
                    group.path("preferences").findValuesAsText("projectTitle"));
        }
        assertEquals(Set.of(2, 5), memberCounts);

        String partnerToken = login("SampleGroupTwo");
        JsonNode partner = json(mockMvc.perform(get("/api/application-periods/{id}/applications/me",
                        period.getId()).header("Authorization", "Bearer " + partnerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("SHARED_SUBMITTED", partner.path("status").asText());
        String invalidToken = login("SampleWithoutRanking");
        mockMvc.perform(get("/api/application-periods/{id}/applications/me", period.getId())
                        .header("Authorization", "Bearer " + invalidToken))
                .andExpect(status().isNotFound());

        UUID catalogueProject = UUID.fromString(periodData.path("projectIds").get(0).asText());
        JsonNode catalogue = json(mockMvc.perform(get("/api/application-periods/{periodId}/catalogue/{projectId}",
                        period.getId(), catalogueProject).header("Authorization", "Bearer " + soloToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("ACCEPTED_FOR_APPLICATIONS",
                store.repository().findById(catalogueProject).orElseThrow().getStatus());
        assertTrue(catalogue.path("specification").path("scope").asText().contains("Local TGM testing content"));

        String previousName = solo.getDisplayName();
        solo.setDisplayName("Subsequent testing edit");
        users.save(solo);
        long priorRecords = store.repository().count();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.unauthenticated("existing-auth", "not-a-secret"));
        importer.importSource(INVENTED_PATH, source);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(priorRecords, store.repository().count());
        UserAccount reloaded = users.findById(solo.getId()).orElseThrow();
        assertEquals("Subsequent testing edit", reloaded.getDisplayName());
        assertNotEquals(previousName, reloaded.getDisplayName());
        byte[] changed = java.util.Arrays.copyOf(source, source.length + 1);
        changed[changed.length - 1] = 1;
        assertThrows(IllegalStateException.class, () -> importer.importSource(INVENTED_PATH, changed));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(priorRecords, store.repository().count());
    }

    @Test
    void rejectsInvalidGroupMatrixWithExactSoloFallbackAndNoDuplicateEffectiveApplications() throws Exception {
        TgmWorkbookReader.WorkbookData workbook = TgmWorkbookFixtures.invalidGroupMatrix();
        TgmImportPlan plan = planner.plan(INVENTED_PATH, workbook);

        Set<String> expectedSoloNames = Set.of(
                "Matrix Valid Solo", "Matrix Size One",
                "Matrix Six One", "Matrix Six Two", "Matrix Six Three",
                "Matrix Six Four", "Matrix Six Five", "Matrix Six Six",
                "Matrix Duplicate One", "Matrix Duplicate Two",
                "Matrix Omit One", "Matrix Omit Two", "Matrix Omit One Extra",
                "Matrix Unknown", "Matrix Overlap One",
                "Matrix Nonreciprocal One", "Matrix Nonreciprocal Two",
                "Matrix Reordered One", "Matrix Reordered Two",
                "Matrix Invalid Member One");
        assertEquals(expectedSoloNames, plan.solos().stream()
                .map(TgmWorkbookReader.StudentRow::displayName).collect(Collectors.toSet()));
        assertEquals(2, plan.groups().size());
        assertEquals(Set.of(Set.of("Matrix Overlap Two", "Matrix Overlap Three"),
                        Set.of("Whitespace One", "Whitespace Two")),
                plan.groups().stream().map(group -> group.members().stream()
                        .map(TgmWorkbookReader.StudentRow::displayName).collect(Collectors.toSet()))
                        .collect(Collectors.toSet()));
        assertEquals(26, plan.students().size());
        assertEquals(4, plan.groups().stream().mapToInt(group -> group.members().size()).sum());
        assertEquals(2, plan.validRankings().values().stream().filter(valid -> !valid).count());

        Map<String, String> skipped = plan.invalidReasons();
        assertEquals("declared roster size is outside 2–5", skipped.get("group:4aHIT!3"));
        for (int row = 4; row <= 9; row++) {
            assertEquals("declared roster size is outside 2–5", skipped.get("group:4aHIT!" + row));
        }
        assertEquals("duplicate declared member", skipped.get("group:4aHIT!10"));
        assertEquals("roster is not reciprocal", skipped.get("group:4aHIT!11"));
        assertEquals("declaration omits its student", skipped.get("group:4aHIT!12"));
        assertEquals("roster is not reciprocal", skipped.get("group:4aHIT!13"));
        assertEquals("declared roster size is outside 2–5", skipped.get("group:4aHIT!14"));
        assertEquals("declared member does not resolve uniquely", skipped.get("group:4aHIT!15"));
        assertEquals("roster is not reciprocal", skipped.get("group:4aHIT!16"));
        assertEquals("roster is not reciprocal", skipped.get("group:4aHIT!17"));
        assertEquals("roster is not reciprocal", skipped.get("group:4aHIT!19"));
        assertEquals("declared roster size is outside 2–5", skipped.get("group:4aHIT!20"));
        assertEquals("member preferences differ or are reordered", skipped.get("group:4aHIT!21"));
        assertEquals("member preferences differ or are reordered", skipped.get("group:4aHIT!22"));
        assertEquals("a member does not have three distinct valid preferences", skipped.get("group:4aHIT!23"));
        assertEquals("fewer than three nonblank preferences", skipped.get("4aHIT!24"));
        assertEquals("a member does not have three distinct valid preferences", skipped.get("group:4aHIT!24"));
        assertEquals("repeated preference", skipped.get("4aHIT!27"));
        assertTrue(skipped.keySet().stream().allMatch(key -> key.matches("(group:)?4aHIT![0-9]+")));
        assertTrue(skipped.values().stream().noneMatch(reason -> workbook.students().stream()
                .anyMatch(student -> reason.contains(student.displayName()))));

        importerFor(workbook).importSource(INVENTED_PATH, "invalid-group-matrix".getBytes(
                java.nio.charset.StandardCharsets.UTF_8));
        StoredRecord period = tgmPeriod();
        String teacherToken = login("teacher01");
        JsonNode groupPage = getPage("/api/application-periods/{periodId}/applying-groups",
                teacherToken, 0, 10, period.getId());
        assertPage(groupPage, 2, 10, 1);
        assertEquals(2, groupPage.path("content").size());
        Set<Set<String>> publicGroupMembers = new java.util.HashSet<>();
        for (JsonNode group : groupPage.path("content")) {
            assertEquals("SUBMITTED", group.path("status").asText());
            publicGroupMembers.add(group.path("members").findValuesAsText("displayName")
                    .stream().collect(Collectors.toSet()));
        }
        assertEquals(Set.of(Set.of("Matrix Overlap Two", "Matrix Overlap Three"),
                        Set.of("Whitespace One", "Whitespace Two")), publicGroupMembers);

        JsonNode applications = getPage("/api/application-periods/{periodId}/applications",
                teacherToken, 0, 100, period.getId());
        assertPage(applications, 20, 100, 1);
        assertEquals(expectedSoloNames, applications.path("content").findValuesAsText("studentDisplayName")
                .stream().collect(Collectors.toSet()));
        Map<String, UUID> matrixProjectIds = store.repository().findAll().stream()
                .filter(record -> record.getType().equals("idea"))
                .filter(record -> workbook.projectTitles().contains(store.parse(record).path("title").asText()))
                .collect(Collectors.toMap(record -> store.parse(record).path("title").asText(), StoredRecord::getId));
        Map<String, TgmWorkbookReader.StudentRow> expectedSolos = workbook.students().stream()
                .filter(row -> expectedSoloNames.contains(row.displayName()))
                .collect(Collectors.toMap(row -> users.findByUsername(row.username()).orElseThrow()
                        .getId().toString(), row -> row));
        assertEquals(20, expectedSolos.size());
        Set<String> seenSolos = new java.util.HashSet<>();
        for (JsonNode application : applications.path("content")) {
            assertTrue(application.path("studentId").isTextual());
            String studentId = application.path("studentId").asText();
            assertTrue(seenSolos.add(studentId), "duplicate fallback applicant");
            assertTrue(expectedSolos.containsKey(studentId), "unexpected fallback applicant");
            assertSourcePreferences(application, expectedSolos.get(studentId), matrixProjectIds);
        }
        assertEquals(expectedSolos.keySet(), seenSolos);
        String fallbackToken = login("MatrixDuplicateOne");
        JsonNode fallback = json(mockMvc.perform(get("/api/application-periods/{periodId}/applications/me",
                        period.getId()).header("Authorization", "Bearer " + fallbackToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals("SUBMITTED", fallback.path("status").asText());
        assertEquals(3, fallback.path("preferences").size());
        for (String invalidUsername : List.of("MatrixInvalidMemberTwo", "MatrixRepeatedRanking")) {
            String invalidToken = login(invalidUsername);
            mockMvc.perform(get("/api/application-periods/{periodId}/applications/me", period.getId())
                            .header("Authorization", "Bearer " + invalidToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void rejectsAmbiguousStudentIdentityAndWhitespaceLoginCollisionsBeforePlanningWrites() {
        List<TgmWorkbookReader.StudentRow> ambiguous = List.of(
                new TgmWorkbookReader.StudentRow("4aHIT", 2, "Invented Duplicate", "InventedDuplicate",
                        TgmWorkbookFixtures.CHOICES, List.of()),
                new TgmWorkbookReader.StudentRow("4bHIT", 2, "Invented  Duplicate", "InventedDuplicate",
                        TgmWorkbookFixtures.CHOICES, List.of()));
        IllegalStateException ambiguousFailure = assertThrows(IllegalStateException.class,
                () -> planner.plan(INVENTED_PATH, workbook(ambiguous)));
        assertTrue(ambiguousFailure.getMessage().contains("login collides after whitespace removal"));
        assertFalse(ambiguousFailure.getMessage().contains("Invented Duplicate"));

        List<TgmWorkbookReader.StudentRow> collision = List.of(
                new TgmWorkbookReader.StudentRow("4aHIT", 2, "Invented A B", "InventedAB",
                        TgmWorkbookFixtures.CHOICES, List.of()),
                new TgmWorkbookReader.StudentRow("4bHIT", 2, "Invented AB", "InventedAB",
                        TgmWorkbookFixtures.CHOICES, List.of()));
        IllegalStateException collisionFailure = assertThrows(IllegalStateException.class,
                () -> planner.plan(INVENTED_PATH, workbook(collision)));
        assertTrue(collisionFailure.getMessage().contains("login collides after whitespace removal"));
        assertFalse(collisionFailure.getMessage().contains("Invented A B"));
    }

    @Test
    void missingLocalSourceFailsWithPathAndNoWorkbookFallback() {
        Path missing = Path.of("missing-invented-" + UUID.randomUUID() + ".xlsx");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> importer().readSource(missing));
        assertTrue(failure.getMessage().contains(missing.toString()));
        assertTrue(failure.getMessage().contains("Unable to read required local TGM workbook"));
    }

    @Test
    void reconcilesPaginatedPublicCollectionsBeyondDefaultPageSizeWithoutLeakingAnonymousData() throws Exception {
        TgmWorkbookReader.WorkbookData workbook = TgmWorkbookFixtures.largeFixture(110, 24);
        importerFor(workbook).importSource(INVENTED_PATH, ("large-fixture-" + UUID.randomUUID())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));

        String adminToken = login("admin01");
        String teacherToken = login("teacher01");
        Set<String> expectedUsernames = workbook.students().stream().map(TgmWorkbookReader.StudentRow::username)
                .collect(Collectors.toSet());
        Map<String, String> expectedClasses = workbook.students().stream().collect(Collectors.toMap(
                TgmWorkbookReader.StudentRow::username, TgmWorkbookReader.StudentRow::sheet));
        Set<String> publicUsernames = new java.util.HashSet<>();
        Map<String, String> studentIdsByUsername = new LinkedHashMap<>();
        for (int page = 0; page < 2; page++) {
            JsonNode usersPage = getStudentPage(adminToken, page);
            assertPage(usersPage, 110, 100, 2);
            assertEquals(page == 0 ? 100 : 10, usersPage.path("content").size());
            for (JsonNode user : usersPage.path("content")) {
                assertTrue(user.has("username") && user.path("username").isTextual());
                assertTrue(user.path("roles").isArray());
                assertEquals(1, user.path("roles").size());
                assertEquals("STUDENT", user.path("roles").get(0).asText());
                assertTrue(user.path("id").isTextual());
                assertTrue(publicUsernames.add(user.path("username").asText()), "duplicate paginated student");
                studentIdsByUsername.put(user.path("username").asText(), user.path("id").asText());
                assertEquals(expectedClasses.get(user.path("username").asText()), user.path("className").asText());
            }
        }
        assertEquals(expectedUsernames, publicUsernames);

        Set<String> expectedTitles = new LinkedHashSet<>(workbook.projectTitles());
        Set<String> publicTitles = new LinkedHashSet<>();
        Map<String, UUID> projectIds = new LinkedHashMap<>();
        for (int page = 0; page < 3; page++) {
            JsonNode ideasPage = json(mockMvc.perform(get("/api/ideas")
                            .header("Authorization", "Bearer " + teacherToken)
                            .param("status", "ACCEPTED_FOR_APPLICATIONS").param("page", Integer.toString(page))
                            .param("size", "10"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertPage(ideasPage, 24, 10, 3);
            for (JsonNode idea : ideasPage.path("content")) {
                assertTrue(idea.path("id").isTextual());
                assertTrue(idea.path("title").isTextual());
                publicTitles.add(idea.path("title").asText());
                projectIds.put(idea.path("title").asText(), UUID.fromString(idea.path("id").asText()));
            }
        }
        assertEquals(expectedTitles, publicTitles);
        StoredRecord period = tgmPeriod();
        String catalogueStudentToken = login("InventedStudent001");
        JsonNode periodData = store.parse(period);
        Set<String> periodProjectIds = new java.util.HashSet<>();
        assertTrue(periodData.path("projectIds").isArray());
        periodData.path("projectIds").forEach(project -> periodProjectIds.add(project.asText()));
        assertEquals(projectIds.values().stream().map(UUID::toString).collect(Collectors.toSet()), periodProjectIds);
        JsonNode periodPage = getPage("/api/application-periods", teacherToken, 0, 1);
        assertPage(periodPage, 1, 1, 1);
        assertEquals(period.getId().toString(), periodPage.path("content").get(0).path("id").asText());
        for (Map.Entry<String, UUID> project : projectIds.entrySet()) {
            JsonNode catalogue = json(mockMvc.perform(get(
                            "/api/application-periods/{periodId}/catalogue/{projectId}",
                            period.getId(), project.getValue())
                            .header("Authorization", "Bearer " + catalogueStudentToken))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            assertEquals(project.getKey(), catalogue.path("title").asText());
            assertTrue(catalogue.path("specification").isObject());
            assertTrue(catalogue.path("specification").path("scope").isTextual());
            assertFalse(catalogue.path("specification").path("scope").asText().isBlank());
            for (String field : List.of("objectives", "intendedUsers", "requirements", "deliverables",
                    "dependencies")) {
                assertTrue(catalogue.path("specification").path(field).isArray());
                assertFalse(catalogue.path("specification").path(field).isEmpty());
            }
        }

        // This fixture declares exactly one reciprocal pair; all undeclared rows are valid solos.
        // Derive expectations from source rows, never from the production planner or application responses.
        Map<String, TgmWorkbookReader.StudentRow> expectedSoloRows = workbook.students().stream()
                .filter(row -> row.declaredMembers().isEmpty())
                .collect(Collectors.toMap(row -> studentIdsByUsername.get(row.username()), row -> row));
        assertEquals(108, expectedSoloRows.size());
        Set<String> applicationStudentIds = new java.util.HashSet<>();
        for (int page = 0; page < 2; page++) {
            JsonNode applicationPage = getPage("/api/application-periods/{periodId}/applications",
                    teacherToken, page, 100, period.getId());
            assertPage(applicationPage, 108, 100, 2);
            for (JsonNode application : applicationPage.path("content")) {
                assertEquals("SUBMITTED", application.path("status").asText());
                assertTrue(application.path("studentId").isTextual());
                String studentId = application.path("studentId").asText();
                assertTrue(applicationStudentIds.add(studentId), "duplicate paginated solo applicant");
                assertTrue(expectedSoloRows.containsKey(studentId), "unexpected paginated solo applicant");
                assertSourcePreferences(application, expectedSoloRows.get(studentId), projectIds);
            }
        }
        assertEquals(108, applicationStudentIds.size());
        assertEquals(expectedSoloRows.keySet(), applicationStudentIds);

        JsonNode groupPage = getPage("/api/application-periods/{periodId}/applying-groups",
                teacherToken, 0, 1, period.getId());
        assertPage(groupPage, 1, 1, 1);
        JsonNode group = groupPage.path("content").get(0);
        assertEquals("SUBMITTED", group.path("status").asText());
        assertEquals(2, group.path("members").size());
        assertTrue(group.path("members").isArray());
        group.path("members").forEach(member -> assertTrue(member.path("studentId").isTextual()));
        List<TgmWorkbookReader.StudentRow> expectedGroupRows = workbook.students().stream()
                .filter(row -> !row.declaredMembers().isEmpty()).toList();
        assertEquals(2, expectedGroupRows.size());
        for (TgmWorkbookReader.StudentRow sourceRow : expectedGroupRows) {
            assertSourcePreferences(group, sourceRow, projectIds);
        }
        Set<String> groupStudentIds = group.path("members").findValuesAsText("studentId")
                .stream().collect(Collectors.toSet());
        assertEquals(2, groupStudentIds.size());
        Set<String> allEffectiveIds = new java.util.HashSet<>(applicationStudentIds);
        assertTrue(java.util.Collections.disjoint(allEffectiveIds, groupStudentIds));
        allEffectiveIds.addAll(groupStudentIds);
        assertEquals(110, allEffectiveIds.size());
        assertEquals(expectedGroupRows.stream().map(row -> studentIdsByUsername.get(row.username()))
                .collect(Collectors.toSet()), groupStudentIds);

        mockMvc.perform(get("/api/users").param("role", "STUDENT"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/application-periods/{periodId}/applications", period.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/application-periods/{periodId}/applying-groups", period.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ideas")).andExpect(status().isUnauthorized());
    }

    private void assertSourcePreferences(JsonNode application, TgmWorkbookReader.StudentRow source,
            Map<String, UUID> projectIds) {
        JsonNode preferences = application.path("preferences");
        assertTrue(preferences.isArray());
        assertEquals(3, preferences.size());
        for (int index = 0; index < 3; index++) {
            JsonNode preference = preferences.get(index);
            assertTrue(preference.isObject());
            assertTrue(preference.path("projectId").isTextual());
            assertTrue(preference.path("projectTitle").isTextual());
            String title = source.choices().get(index).trim();
            assertTrue(projectIds.containsKey(title));
            assertEquals(projectIds.get(title).toString(), preference.path("projectId").asText(),
                    source.sheet() + "!" + source.row() + " preference " + index);
            assertEquals(title, preference.path("projectTitle").asText());
        }
    }

    private TestingTgmWorkbookImporter importerFor(TgmWorkbookReader.WorkbookData workbook) {
        TgmWorkbookReader sourceReader = mock(TgmWorkbookReader.class);
        when(sourceReader.read(any(Path.class), any(byte[].class))).thenReturn(workbook);
        return new TestingTgmWorkbookImporter(environment, sourceReader, planner, writer);
    }

    private TgmWorkbookReader.WorkbookData workbook(List<TgmWorkbookReader.StudentRow> students) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        TgmWorkbookReader.SHEETS.forEach(sheet -> counts.put(sheet,
                (int) students.stream().filter(student -> student.sheet().equals(sheet)).count()));
        return new TgmWorkbookReader.WorkbookData(students,
                students.stream().flatMap(student -> student.choices().stream()).distinct().toList(), counts);
    }

    private JsonNode getPage(String route, String token, int page, int size, Object... pathVariables)
            throws Exception {
        return json(mockMvc.perform(get(route, pathVariables).header("Authorization", "Bearer " + token)
                        .param("page", Integer.toString(page)).param("size", Integer.toString(size)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode getStudentPage(String token, int page) throws Exception {
        return json(mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + token)
                        .param("role", "STUDENT").param("q", "Invented Student")
                        .param("page", Integer.toString(page)).param("size", "100"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void assertPage(JsonNode page, int total, int size, int pages) {
        assertTrue(page.path("content").isArray());
        assertTrue(page.has("totalElements") && page.path("totalElements").isIntegralNumber());
        assertTrue(page.has("size") && page.path("size").isIntegralNumber());
        assertTrue(page.has("totalPages") && page.path("totalPages").isIntegralNumber());
        assertEquals(total, page.path("totalElements").asInt());
        assertEquals(size, page.path("size").asInt());
        assertEquals(pages, page.path("totalPages").asInt());
    }

    private TestingTgmWorkbookImporter importer() {
        return new TestingTgmWorkbookImporter(environment, reader, planner, writer);
    }

    private StoredRecord tgmPeriod() {
        return store.repository().findByTypeAndParentId("application_period", null)
                .stream().filter(record -> "Diplomarbeiten 2026/27".equals(store.parse(record).path("label").asText()))
                .findFirst().orElseThrow();
    }

    private String login(String username) throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", username, "password", PASSWORD));
        JsonNode response = json(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(username, response.path("user").path("username").asText());
        return response.path("token").asText();
    }

    private JsonNode json(String text) throws Exception {
        return mapper.readTree(text);
    }
}
