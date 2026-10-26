package ac.at.tgm.submit.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ac.at.tgm.submit.data.RecordStore;
import ac.at.tgm.submit.data.StoredRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ac.at.tgm.submit.user.UserAccountRepository;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:applyinggroups;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE")
@ActiveProfiles("testing")
@AutoConfigureMockMvc
class ApplyingGroupApiTest {
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    @Autowired RecordStore store;
    @Autowired UserAccountRepository users;
    UUID period;
    List<UUID> projects;
    Map<Integer, String> tokens = new HashMap<>();
    String admin;
    String teacher;

    @BeforeEach
    void prepare() throws Exception {
        for (int i = 1; i <= 7; i++) tokens.put(i, login("student%02d".formatted(i)));
        admin = login("admin01");
        perform(patch("/api/users/" + users.findByUsername("teacher01").orElseThrow().getId() + "/role"),
                admin, Map.of("roles", List.of("TEACHER")), 200);
        teacher = login("teacher01");
        projects = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            projects.add(store.create("idea", student(1), null, "ACCEPTED_FOR_APPLICATIONS",
                    store.object().put("title", "Shared project " + i).put("schoolYear", "2026/2027")).getId());
        }
        var data = store.object().put("label", "Shared application test").put("schoolYear", "2026/2027")
                .put("opensAt", Instant.now().minusSeconds(60).toString())
                .put("closesAt", Instant.now().plusSeconds(3600).toString())
                .put("eligibilityMode", "SELECTED");
        data.set("projectIds", store.value(projects));
        data.set("eligibleStudentIds", store.value(List.of(users.findByUsername("student01")
                .orElseThrow().getId())));
        data.putArray("eligibleClassIds");
        period = store.create("application_period", null, null, "OPEN", data).getId();
    }

    @Test
    void G1_atomicUnanimousRevisionBoundReplacementAndAuthorization() throws Exception {
        String teacherOnly = teacher;
        setRoles(users.findByUsername("teacher01").orElseThrow().getId().toString(),
                List.of("TEACHER", "ADMIN"));
        teacher = login("teacher01");
        String solo1 = solo(1).path("id").asText();
        String solo2 = solo(2).path("id").asText();
        JsonNode group = create(1);
        String id = text(group, "id");
        change(1, id, "INVITE", revision(id), Map.of("studentId", student(2)), 200);
        assertSoloStatus(solo1, "SUBMITTED");
        assertSoloStatus(solo2, "SUBMITTED");
        change(3, id, "ACCEPT", revision(id), Map.of(), 403);
        change(2, id, "ACCEPT", revision(id), Map.of(), 200);
        change(2, id, "RANK", revision(id), Map.of("projectIds", projects), 403);
        change(1, id, "RANK", revision(id), Map.of("projectIds", projects), 200);
        long current = revision(id);
        change(1, id, "SUBMIT", current, Map.of("confirm", true), 409);
        change(1, id, "APPROVE", current, Map.of("studentId", student(2)), 400);
        change(1, id, "APPROVE", current, Map.of(), 200);
        change(1, id, "SUBMIT", current, Map.of("confirm", true), 409);
        assertSoloStatus(solo1, "SUBMITTED");
        assertSoloStatus(solo2, "SUBMITTED");
        change(2, id, "APPROVE", current - 1, Map.of(), 409);
        change(2, id, "APPROVE", current, Map.of(), 200);
        change(1, id, "SUBMIT", current, Map.of("confirm", true), 200);
        assertSoloStatus(solo1, "REPLACED");
        assertSoloStatus(solo2, "REPLACED");
        assertEquals("SUBMITTED", stored(id).getStatus());
        perform(put("/api/applications/" + solo2), tokens.get(2), Map.of("projectIds", projects), 409);
        perform(delete("/api/applications/" + solo1), tokens.get(1), null, 409);
        perform(post("/api/application-periods/" + period + "/applications"), tokens.get(2),
                Map.of("projectIds", projects), 409);
        JsonNode own = perform(get("/api/application-periods/" + period), tokens.get(2), null, 200);
        assertEquals("SHARED_SUBMITTED", text(own, "studentApplicationStatus"));
        JsonNode effective = perform(get("/api/application-periods/" + period + "/applications/me"), tokens.get(2), null, 200);
        assertEquals(id, text(effective, "id"));
        assertEquals(student(2), text(effective, "studentId"));
        assertEquals("SHARED_SUBMITTED", text(effective, "status"));
        assertEquals(projects.stream().map(UUID::toString).toList(), java.util.stream.StreamSupport
                .stream(array(effective, "preferences").spliterator(), false)
                .map(preference -> text(preference, "projectId")).toList());
        perform(get(path() + "/" + id), tokens.get(3), null, 403);
        perform(get(path() + "/" + id), teacher, null, 200);
        perform(get(path() + "/" + id), admin, null, 200);
        perform(get(path() + "/" + id), null, null, 401);
        for (String action : List.of("INVITE", "REVOKE", "ACCEPT", "DECLINE", "APPROVE", "RANK",
                "SUBMIT", "WITHDRAW", "LEAVE", "TRANSFER", "DISSOLVE")) {
            String before = stored(id).getPayload();
            perform(post(path() + "/" + id + "/actions/" + action), teacher, Map.of("revision", current), 403);
            perform(post(path() + "/" + id + "/actions/" + action), admin, Map.of("revision", current), 403);
            perform(post(path() + "/" + id + "/actions/" + action), null, Map.of("revision", current), 401);
            assertEquals(before, stored(id).getPayload());
        }
        long groupCountBeforeCreateDenials = store.repository().findByType("applying_group",
                org.springframework.data.domain.PageRequest.of(0, 1000)).getTotalElements();
        long auditCountBeforeCreateDenials = auditCount();
        for (String staff : List.of(teacherOnly, admin, teacher)) {
            perform(post(path()), staff, null, 403);
            assertEquals(groupCountBeforeCreateDenials, store.repository().findByType("applying_group",
                    org.springframework.data.domain.PageRequest.of(0, 1000)).getTotalElements());
            assertEquals(auditCountBeforeCreateDenials, auditCount());
        }
        setRoles(users.findByUsername("teacher01").orElseThrow().getId().toString(), List.of("TEACHER"));
        for (String action : List.of("INVITE", "REVOKE", "ACCEPT", "DECLINE", "APPROVE", "RANK",
                "SUBMIT", "WITHDRAW", "LEAVE", "TRANSFER", "DISSOLVE")) {
            String before = stored(id).getPayload();
            for (String staff : List.of(teacherOnly, teacher)) {
                long auditsBeforeAction = auditCount();
                perform(post(path() + "/" + id + "/actions/" + action), staff,
                        Map.of("revision", current), 403);
                assertEquals(auditsBeforeAction, auditCount(),
                        "A forbidden " + action + " must not emit an audit record.");
            }
            assertEquals(before, stored(id).getPayload());
        }
    }

    @Test
    void G2_revisionsDeclineTransferDepartureDissolutionAndNoRestoration() throws Exception {
        String previous = solo(2).path("id").asText();
        String id = submittedPair();
        long revision = revision(id);
        change(1, id, "INVITE", revision, Map.of("studentId", student(3)), 200);
        change(3, id, "DECLINE", revision, Map.of(), 200);
        assertEquals(revision, revision(id));
        assertEquals("SUBMITTED", stored(id).getStatus());
        change(1, id, "LEAVE", revision, Map.of(), 409);
        change(1, id, "TRANSFER", revision, Map.of("studentId", student(2)), 200);
        assertEquals(revision, revision(id));
        change(1, id, "WITHDRAW", revision, Map.of(), 403);
        change(1, id, "LEAVE", revision, Map.of(), 200);
        assertEquals("DRAFT", stored(id).getStatus());
        assertTrue(object(store.parse(stored(id)), "approvals").isEmpty());
        assertEquals(1, array(store.parse(stored(id)), "members").size());
        assertSoloStatus(previous, "REPLACED");
        change(2, id, "SUBMIT", revision(id), Map.of("confirm", true), 409);
        change(2, id, "DISSOLVE", revision(id), Map.of("confirm", false), 409);
        change(2, id, "DISSOLVE", revision(id), Map.of("confirm", true), 200);
        assertEquals("DISSOLVED", stored(id).getStatus());
        assertEquals("NOT_SUBMITTED", text(perform(get("/api/application-periods/" + period), tokens.get(2), null, 200),
                "studentApplicationStatus"));
        perform(put("/api/applications/" + previous), tokens.get(2), Map.of("projectIds", projects), 200);
        assertSoloStatus(previous, "SUBMITTED");
    }

    @Test
    void G2_deadlineLocksEveryMutationAndConcurrentFinalSeatsAreAtomic() throws Exception {
        String id = text(create(1), "id");
        for (int i = 2; i <= 7; i++) change(1, id, "INVITE", revision(id), Map.of("studentId", student(i)), 200);
        for (int i = 2; i <= 4; i++) change(i, id, "ACCEPT", revision(id), Map.of(), 200);
        long revision = revision(id);
        var ready = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { ready.await(); return actionStatus(5, id, "ACCEPT", revision); });
            var two = pool.submit(() -> { ready.await(); return actionStatus(6, id, "ACCEPT", revision); });
            ready.countDown();
            assertEquals(Set.of(200, 409), Set.of(one.get(), two.get()));
        }
        assertEquals(5, array(store.parse(stored(id)), "members").size());
        StoredRecord p = store.get("application_period", period);
        var data = store.parse(p).put("closesAt", Instant.now().toString());
        store.update(p, "OPEN", data);
        for (String action : List.of("INVITE", "REVOKE", "ACCEPT", "DECLINE", "APPROVE", "RANK", "SUBMIT",
                "WITHDRAW", "LEAVE", "TRANSFER", "DISSOLVE")) {
            String before = stored(id).getPayload();
            change(1, id, action, revision(id), Map.of("studentId", student(7), "projectIds", projects, "confirm", true), 409);
            assertEquals(before, stored(id).getPayload());
        }
        perform(post(path()), tokens.get(7), null, 409);
    }

    @Test
    void G3_G4_G5_pairPlusSoloPinsImmutableRunsAndPrivateFinalOrigin() throws Exception {
        String id = submittedPair();
        solo(3);
        String pinPath = "/api/application-periods/" + period + "/allocation-pins/";
        perform(put(pinPath + student(1)), admin, Map.of("projectId", projects.getFirst()), 200);
        perform(put(pinPath + student(2)), admin, Map.of("projectId", projects.get(1)), 409);
        perform(put(pinPath + student(2)), admin, Map.of("projectId", projects.getFirst()), 200);
        JsonNode early = run();
        assertEquals(3, integer(early.path("result"), "assignedStudents"));
        assertEquals(9, integer(early.path("result"), "preferenceScore"));
        JsonNode assignments = early.path("result").path("assignments");
        assertTrue(assignments.isArray());
        assertEquals(3, assignments.size());
        for (JsonNode row : assignments) assertEquals(projects.getFirst().toString(), text(row, "projectId"));
        assertEquals(1, array(early, "applyingUnits").size());
        String firstId = text(early, "id");
        String snapshot = stored(firstId).getPayload();
        JsonNode studentPeriod = perform(get("/api/application-periods/" + period), tokens.get(1), null, 200);
        assertEquals("NO_RESULT", text(studentPeriod.path("studentAssignment"), "state"));
        perform(delete(pinPath + student(1)), admin, null, 204);
        JsonNode again = run();
        assertEquals(3, integer(again.path("result"), "assignedStudents"));
        assertEquals(snapshot, stored(firstId).getPayload());
        StoredRecord p = store.get("application_period", period);
        store.update(p, "CLOSED", store.parse(p).put("closesAt", Instant.now().minusSeconds(1).toString()));
        JsonNode replacement = run();
        perform(post("/api/allocation-runs/" + text(replacement, "id") + "/finalize"), admin, null, 200);
        studentPeriod = perform(get("/api/application-periods/" + period), tokens.get(2), null, 200);
        JsonNode result = studentPeriod.path("studentAssignment");
        assertEquals("FINAL", text(result, "state"));
        assertEquals(3, array(result, "groupMembers").size());
        int ownCohort = 0;
        int individuals = 0;
        for (JsonNode member : result.path("groupMembers")) {
            assertTrue(member.isObject());
            assertEquals(Set.of("displayName", "self", "originLabel", "ownCohort"), member.propertyStream()
                    .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
            assertTrue(member.path("ownCohort").isBoolean());
            if (member.path("ownCohort").booleanValue()) ownCohort++;
            if (text(member, "originLabel").startsWith("Applied individually")) individuals++;
        }
        assertEquals(2, ownCohort);
        assertEquals(1, individuals);
        assertEquals(snapshot, stored(firstId).getPayload());
        assertEquals("SUBMITTED", stored(id).getStatus());
        var proposal = perform(post("/api/projects/" + projects.getFirst() + "/formal-applications"),
                tokens.get(3), formalBody(List.of(1, 2, 3)), 201);
        assertTrue(proposal.isObject());
        perform(get("/api/projects/" + projects.getFirst() + "/formal-applications"), tokens.get(3), null, 200);
    }

    @Test
    void G1_fiveMembersInvalidRequestsAndIdempotentAtomicReplacement() throws Exception {
        List<String> solos = new ArrayList<>();
        for (int i = 1; i <= 5; i++) solos.add(text(solo(i), "id"));
        String id = text(create(1), "id");
        rejectedUnchanged(1, id, "INVITE", Map.of("studentId", student(1)), 409);
        rejectedUnchanged(1, id, "INVITE", Map.of("studentId",
                users.findByUsername("teacher01").orElseThrow().getId()), 400);
        change(1, id, "RANK", revision(id), Map.of("projectIds", projects), 200);
        change(1, id, "APPROVE", revision(id), Map.of(), 200);
        rejectedUnchanged(1, id, "SUBMIT", Map.of("confirm", true), 409);
        for (int i = 2; i <= 6; i++) change(1, id, "INVITE", revision(id), Map.of("studentId", student(i)), 200);
        rejectedUnchanged(1, id, "INVITE", Map.of("studentId", student(2)), 409);
        for (int i = 2; i <= 5; i++) change(i, id, "ACCEPT", revision(id), Map.of(), 200);
        rejectedUnchanged(6, id, "ACCEPT", Map.of(), 409);
        rejectedUnchanged(2, id, "ACCEPT", Map.of(), 403);
        rejectedUnchanged(1, id, "RANK", Map.of("projectIds", List.of(projects.getFirst(), projects.getFirst(), projects.get(1))), 400);
        rejectedUnchanged(1, id, "RANK", Map.of("projectIds", List.of(projects.getFirst(), projects.get(1), UUID.randomUUID())), 400);
        rejectedUnchanged(1, id, "RANK", Map.of("projectIds", List.of(projects.getFirst())), 400);
        for (int i = 1; i <= 5; i++) {
            assertSoloStatus(solos.get(i - 1), "SUBMITTED");
            change(i, id, "APPROVE", revision(id), Map.of(), 200);
        }
        change(1, id, "SUBMIT", revision(id), Map.of("confirm", true), 200);
        for (String solo : solos) {
            assertSoloStatus(solo, "REPLACED");
            assertEquals(1, store.repository().findByTypeAndParentId("student_application_revision", solo).size());
        }
        change(1, id, "SUBMIT", revision(id), Map.of("confirm", true), 200);
        for (String solo : solos) assertEquals(1,
                store.repository().findByTypeAndParentId("student_application_revision", solo).size());
        var input = run();
        assertEquals(5, array(array(input, "applyingUnits").get(0), "studentIds").size());
        assertEquals(5, integer(input.get("result"), "assignedStudents"));
    }

    @Test
    void G1_G6_eachNewOperationRoleAndCrossPeriodMatrix() throws Exception {
        String id = text(create(1), "id");
        change(1, id, "INVITE", revision(id), Map.of("studentId", student(2)), 200);
        change(2, id, "ACCEPT", revision(id), Map.of(), 200);
        change(1, id, "INVITE", revision(id), Map.of("studentId", student(3)), 200);
        String teacherOnly = teacher;
        setRoles(users.findByUsername("teacher01").orElseThrow().getId().toString(),
                List.of("TEACHER", "ADMIN"));
        String dual = login("teacher01");
        String[] actors = {tokens.get(1), tokens.get(2), tokens.get(3), tokens.get(4),
                teacherOnly, dual, admin, null};
        int[] read = {200, 200, 200, 403, 200, 200, 200, 401};
        int[] invites = {200, 403, 403, 403, 200, 200, 200, 401};
        int[] approve = {200, 200, 403, 403, 403, 403, 403, 401};
        for (int i = 0; i < actors.length; i++) {
            perform(get(path()), actors[i], null, i == 7 ? 401 : 200);
            perform(get(path() + "/" + id), actors[i], null, read[i]);
            perform(get(path() + "/" + id + "/invitations"), actors[i], null, invites[i]);
            String beforeDeniedAction = stored(id).getPayload();
            long auditsBeforeDeniedAction = auditCount();
            perform(post(path() + "/" + id + "/actions/APPROVE"), actors[i],
                    Map.of("revision", revision(id)), approve[i]);
            if (i >= 4 && i <= 6) {
                assertEquals(beforeDeniedAction, stored(id).getPayload());
                assertEquals(auditsBeforeDeniedAction, auditCount());
            }
            if (i >= 4) perform(post(path()), actors[i], null, i == 7 ? 401 : 403);
        }
        setRoles(users.findByUsername("teacher01").orElseThrow().getId().toString(), List.of("TEACHER"));
        UUID other = store.create("application_period", null, null, "OPEN",
                store.parse(store.get("application_period", period))).getId();
        String foreign = "/api/application-periods/" + other + "/applying-groups/" + id;
        String before = stored(id).getPayload();
        perform(get(foreign), tokens.get(1), null, 404);
        perform(get(foreign + "/invitations"), tokens.get(1), null, 404);
        perform(post(foreign + "/actions/APPROVE"), tokens.get(1), Map.of("revision", revision(id)), 404);
        assertEquals(before, stored(id).getPayload());
        perform(get("/api/application-periods/not-a-uuid/applying-groups"), tokens.get(1), null, 400);
        perform(get("/api/application-periods/" + UUID.randomUUID() + "/applying-groups"), tokens.get(1), null, 404);
        var unrelated = perform(get(path()), tokens.get(4), null, 200);
        assertTrue(unrelated.get("content").isArray());
        assertTrue(unrelated.get("content").isEmpty());
    }

    @Test
    void G2_allMutationClosedFinalizedAndExpiredBoundariesAndCompetingAcceptances() throws Exception {
        String one = text(create(1), "id");
        String two = text(create(2), "id");
        change(1, one, "INVITE", revision(one), Map.of("studentId", student(3)), 200);
        change(2, two, "INVITE", revision(two), Map.of("studentId", student(3)), 200);
        var ready = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { ready.await(); return actionStatus(3, one, "ACCEPT", revision(one)); });
            var second = pool.submit(() -> { ready.await(); return actionStatus(3, two, "ACCEPT", revision(two)); });
            ready.countDown();
            assertEquals(Set.of(200, 409), Set.of(first.get(), second.get()));
        }
        long occurrences = List.of(one, two).stream().map(this::stored).map(store::parse)
                .flatMap(data -> java.util.stream.StreamSupport.stream(data.path("members").spliterator(), false))
                .filter(member -> member.textValue().equals(student(3))).count();
        assertEquals(1, occurrences);
        StoredRecord p = store.get("application_period", period);
        for (String state : List.of("CLOSED", "FINALIZED", "OPEN")) {
            store.update(p, state, store.parse(p).put("closesAt", "FINALIZED".equals(state)
                    ? Instant.now().plusSeconds(3600).toString() : Instant.now().minusSeconds(1).toString()));
            for (String action : List.of("INVITE", "REVOKE", "ACCEPT", "DECLINE", "APPROVE", "RANK",
                    "SUBMIT", "WITHDRAW", "LEAVE", "TRANSFER", "DISSOLVE")) {
                rejectedUnchanged(1, one, action, Map.of("studentId", student(7), "projectIds", projects, "confirm", true), 409);
            }
            perform(post(path()), tokens.get(7), null, 409);
        }
    }

    @Test
    void G2_rankingWithdrawalAndDissolutionNeverRestoreSoloEligibility() throws Exception {
        String first = text(solo(1), "id");
        String second = text(solo(2), "id");
        String id = submittedPair();
        long oldRevision = revision(id);
        var reordered = List.of(projects.get(1), projects.getFirst(), projects.get(2));
        change(1, id, "RANK", oldRevision, Map.of("projectIds", reordered), 200);
        assertEquals(oldRevision + 1, revision(id));
        assertEquals("DRAFT", stored(id).getStatus());
        assertTrue(object(store.parse(stored(id)), "approvals").isEmpty());
        change(1, id, "SUBMIT", oldRevision, Map.of("confirm", true), 409);
        for (int i : List.of(1, 2)) change(i, id, "APPROVE", revision(id), Map.of(), 200);
        change(1, id, "SUBMIT", revision(id), Map.of("confirm", true), 200);
        change(1, id, "WITHDRAW", revision(id), Map.of(), 200);
        assertEquals("DRAFT", stored(id).getStatus());
        assertTrue(object(store.parse(stored(id)), "approvals").isEmpty());
        assertSoloStatus(first, "REPLACED");
        assertSoloStatus(second, "REPLACED");
        change(1, id, "DISSOLVE", revision(id), Map.of("confirm", true), 200);
        for (int i : List.of(1, 2)) {
            var list = perform(get(path()), tokens.get(i), null, 200);
            assertTrue(list.get("content").isArray());
            assertEquals("DISSOLVED", text(list.get("content").get(0), "status"));
            assertEquals("NOT_SUBMITTED", text(perform(get("/api/application-periods/" + period), tokens.get(i), null, 200),
                    "studentApplicationStatus"));
        }
    }

    @Test
    void G4_effectiveCapacityAcknowledgementRemovalAndSubmissionConflictRollback() throws Exception {
        String id = submittedPair();
        for (int i = 3; i <= 6; i++) solo(i);
        String pinPath = "/api/application-periods/" + period + "/allocation-pins/";
        perform(put(pinPath + student(1)), admin, Map.of("projectId", projects.getFirst()), 200);
        perform(put(pinPath + student(2)), admin, Map.of("projectId", projects.getFirst()), 200);
        for (int i = 3; i <= 5; i++) perform(put(pinPath + student(i)), admin,
                Map.of("projectId", projects.getFirst()), 200);
        String pinsBefore = pinBytes();
        perform(put(pinPath + student(6)), admin, Map.of("projectId", projects.getFirst()), 409);
        assertEquals(pinsBefore, pinBytes());
        perform(put(pinPath + student(2)), admin, Map.of("projectId", projects.get(1)), 409);
        assertEquals(pinsBefore, pinBytes());
        var old = run();
        String oldBytes = stored(text(old, "id")).getPayload();
        perform(delete(pinPath + student(1)), admin, null, 204);
        var remaining = run().get("result").get("assignments");
        assertTrue(remaining.isArray());
        assertEquals(5, remaining.size());
        for (var assignment : remaining) assertEquals(projects.getFirst().toString(), text(assignment, "projectId"));
        perform(delete(pinPath + student(2)), admin, null, 204);
        var released = run().get("result");
        assertEquals(6, integer(released, "assignedStudents"));
        for (var assignment : array(released, "assignments")) {
            if (Set.of(student(1), student(2)).contains(text(assignment, "studentId"))) {
                assertEquals(projects.get(1).toString(), text(assignment, "projectId"));
            }
        }
        for (int i = 3; i <= 5; i++) perform(delete(pinPath + student(i)), admin, null, 204);
        UUID unranked = store.create("idea", student(1), null, "ACCEPTED_FOR_APPLICATIONS",
                store.object().put("title", "Unranked shared catalogue target").put("schoolYear", "2026/2027")).getId();
        var p = store.get("application_period", period);
        var data = store.parse(p);
        data.withArray("projectIds").add(unranked.toString());
        perform(put("/api/application-periods/" + period), admin, data, 200);
        perform(put(pinPath + student(1)), admin, Map.of("projectId", unranked), 409);
        perform(put(pinPath + student(1)), admin, Map.of("projectId", unranked, "acknowledgeUnranked", true), 200);
        String periodBefore = store.get("application_period", period).getPayload();
        var removal = store.parse(store.get("application_period", period));
        removal.set("projectIds", store.value(projects));
        perform(put("/api/application-periods/" + period), admin, removal, 409);
        assertEquals(periodBefore, store.get("application_period", period).getPayload());
        var infeasible = run().get("result");
        assertEquals(4, integer(infeasible, "assignedStudents"));
        assertTrue(infeasible.get("unassignedStudents").isArray());
        assertEquals(Set.of(student(1), student(2)), java.util.stream.StreamSupport
                .stream(infeasible.get("unassignedStudents").spliterator(), false)
                .map(row -> text(row, "studentId")).collect(java.util.stream.Collectors.toSet()));
        assertEquals(oldBytes, stored(text(old, "id")).getPayload());
        perform(delete(pinPath + student(1)), admin, null, 204);
        perform(put(pinPath + student(7)), admin, Map.of("projectId", projects.getFirst()), 409);
        perform(put(pinPath + student(7)), admin,
                Map.of("projectId", projects.getFirst(), "acknowledgeUnranked", true), 200);
        perform(put(pinPath + student(4)), admin, Map.of("projectId", projects.getFirst()), 200);
        perform(put(pinPath + student(5)), admin, Map.of("projectId", projects.get(1)), 200);
        String draft = text(create(4), "id");
        change(4, draft, "INVITE", revision(draft), Map.of("studentId", student(5)), 200);
        change(5, draft, "ACCEPT", revision(draft), Map.of(), 200);
        change(4, draft, "RANK", revision(draft), Map.of("projectIds", projects), 200);
        for (int member : List.of(4, 5)) change(member, draft, "APPROVE", revision(draft), Map.of(), 200);
        pinsBefore = pinBytes();
        rejectedUnchanged(4, draft, "SUBMIT", Map.of("confirm", true), 409);
        assertEquals(pinsBefore, pinBytes());
        for (int member : List.of(4, 5)) assertEquals("SUBMITTED", store.repository()
                .findByTypeAndParentIdAndOwnerId("student_application", period.toString(), student(member)).orElseThrow().getStatus());
        // Five explicit effective students fit while this pair is still a draft.
        // Activating it would expand member 4's pin to member 5 and exceed capacity.
        perform(delete(pinPath + student(5)), admin, null, 204);
        for (int member : List.of(3, 6, 8)) perform(put(pinPath + student(member)), admin,
                Map.of("projectId", projects.getFirst(), "acknowledgeUnranked", true), 200);
        pinsBefore = pinBytes();
        rejectedUnchanged(4, draft, "SUBMIT", Map.of("confirm", true), 409);
        assertEquals(pinsBefore, pinBytes());
        for (int member : List.of(4, 5)) assertEquals("SUBMITTED", store.repository()
                .findByTypeAndParentIdAndOwnerId("student_application", period.toString(), student(member)).orElseThrow().getStatus());
        assertEquals("SUBMITTED", stored(id).getStatus());
        var draftRun = run();
        assertEquals(1, array(draftRun, "applyingUnits").size());
        assertEquals(id, text(draftRun.get("applyingUnits").get(0), "groupId"));
        perform(get("/api/application-periods/" + period + "/allocation-pins"), tokens.get(1), null, 403);
    }

    @Test
    void G5_realAutomaticInitialReplacementTwoCohortsAndImmutableHistory() throws Exception {
        String pair = submittedPair();
        submittedUnit(List.of(3, 4, 5));
        var early = run();
        assertEquals(5, integer(early.get("result"), "assignedStudents"));
        String earlyId = text(early, "id");
        String bytes = stored(earlyId).getPayload();
        change(1, pair, "RANK", revision(pair),
                Map.of("projectIds", List.of(projects.get(1), projects.getFirst(), projects.get(2))), 200);
        for (int member : List.of(1, 2)) change(member, pair, "APPROVE", revision(pair), Map.of(), 200);
        change(1, pair, "SUBMIT", revision(pair), Map.of("confirm", true), 200);
        perform(put("/api/application-periods/" + period + "/allocation-pins/" + student(1)),
                admin, Map.of("projectId", projects.getFirst()), 200);
        assertEquals(bytes, stored(earlyId).getPayload());
        var p = store.get("application_period", period);
        var data = store.parse(p).put("closesAt", Instant.now().plusMillis(250).toString());
        perform(put("/api/application-periods/" + period), admin, data, 200);
        JsonNode initial = null;
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(45);
        while (initial == null && System.nanoTime() < deadline) {
            var runs = perform(get("/api/application-periods/" + period + "/allocation-runs"), admin, null, 200).get("content");
            assertNotNull(runs);
            assertTrue(runs.isArray());
            for (var run : runs) {
                assertNotNull(run.get("initial"));
                assertTrue(run.get("initial").isBoolean());
                if (run.get("initial").booleanValue()) initial = run;
            }
            if (initial == null) Thread.sleep(100);
        }
        assertNotNull(initial, "The real deadline scheduler must publish an initial run");
        assertTrue(initial.get("applyingUnits").isArray());
        assertEquals(2, array(initial, "applyingUnits").size());
        var preliminary = perform(get("/api/application-periods/" + period), tokens.get(4), null, 200).get("studentAssignment");
        assertEquals("PRELIMINARY", text(preliminary, "state"));
        assertEquals(5, array(preliminary, "groupMembers").size());
        var replacement = run();
        assertFalse(bool(replacement, "initial"));
        assertFalse(bool(replacement, "early"));
        perform(post("/api/allocation-runs/" + text(replacement, "id") + "/finalize"), admin, null, 200);
        var runs = array(perform(get("/api/application-periods/" + period + "/allocation-runs"), admin, null, 200), "content");
        assertEquals(1, java.util.stream.StreamSupport.stream(runs.spliterator(), false)
                .filter(run -> bool(run, "initial")).count());
        var result = perform(get("/api/application-periods/" + period), tokens.get(4), null, 200).get("studentAssignment");
        assertEquals("FINAL", text(result, "state"));
        Set<String> labels = new java.util.HashSet<>();
        int own = 0;
        for (var member : array(result, "groupMembers")) {
            labels.add(text(member, "originLabel"));
            assertTrue(member.get("ownCohort").isBoolean());
            if (member.get("ownCohort").booleanValue()) own++;
        }
        assertEquals(Set.of("Applying cohort 1", "Applying cohort 2"), labels);
        assertEquals(3, own);
        assertEquals(bytes, stored(earlyId).getPayload());
        var finalizedGroups = store.repository().findByTypeAndParentId("project_group", projects.getFirst().toString());
        var persisted = finalizedGroups.stream().filter(group -> period.toString()
                .equals(store.parse(group).path("periodId").textValue())).findFirst().orElseThrow();
        assertEquals(5, array(store.parse(persisted), "studentIds").size());
        assertEquals(2, array(store.parse(persisted), "applyingUnits").size());
    }

    @Test
    void G5_legacySoloRunAndFinalGroupHaveUnavailableNotInventedOrigin() throws Exception {
        for (int member : List.of(1, 2, 3)) solo(member);
        var assignments = store.object().putArray("assignments");
        for (int member : List.of(1, 2, 3)) assignments.addObject()
                .put("studentId", student(member)).put("projectId", projects.getFirst().toString()).put("preferenceRank", 1);
        var payload = store.object();
        payload.set("result", store.object().set("assignments", assignments));
        var legacyRun = store.create("allocation_run", null, period.toString(), "FINALIZED", payload);
        var group = store.object().put("periodId", period.toString()).put("allocationRunId", legacyRun.getId().toString());
        group.set("studentIds", store.value(List.of(student(1), student(2), student(3))));
        store.create("project_group", null, projects.getFirst().toString(), "ALLOCATED", group);
        var p = store.get("application_period", period);
        store.update(p, "FINALIZED", store.parse(p));
        var legacy = perform(get("/api/allocation-runs/" + legacyRun.getId()), admin, null, 200);
        assertFalse(legacy.has("applyingUnits"));
        var result = perform(get("/api/application-periods/" + period), tokens.get(1), null, 200).get("studentAssignment");
        assertEquals("FINAL", text(result, "state"));
        assertEquals(3, array(result, "groupMembers").size());
        for (var member : array(result, "groupMembers")) {
            assertEquals("Origin unavailable", text(member, "originLabel"));
            assertTrue(member.get("ownCohort").isBoolean());
            assertFalse(member.get("ownCohort").booleanValue());
        }
    }

    @Test
    void G6_anonymousArchiveMetadataAndFileExcludeAllSharedApplicationSentinels() throws Exception {
        String id = submittedPair();
        var privateData = store.object().put("invite", "INVITE_PRIVATE_SENTINEL").put("consent", "CONSENT_PRIVATE_SENTINEL")
                .put("roster", "ROSTER_PRIVATE_SENTINEL").put("origin", "ORIGIN_PRIVATE_SENTINEL").put("groupId", id);
        var project = store.get("idea", projects.getFirst());
        var projectData = store.parse(project);
        projectData.set("applyingGroup", privateData);
        store.update(project, "PROJECT_COMPLETED", projectData);
        byte[] bytes = "safe approved file".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var artifactData = store.object().put("type", "POSTER").put("size", bytes.length)
                .put("publicationStatus", "APPROVED").put("title", "INVITE_PRIVATE_SENTINEL")
                .put("description", "CONSENT_PRIVATE_SENTINEL").put("filename", "ROSTER_PRIVATE_SENTINEL")
                .put("reviewNote", "ORIGIN_PRIVATE_SENTINEL")
                .put("content", java.util.Base64.getEncoder().encodeToString(bytes));
        artifactData.set("applyingGroup", privateData);
        var artifact = store.create("artifact", student(1), project.getId().toString(), "APPROVED", artifactData);
        String archivePath = "/api/public/projects/" + project.getId() + "/archive";
        var metadata = perform(get(archivePath), null, null, 200);
        var content = array(metadata, "content");
        assertEquals(1, content.size());
        assertEquals(Set.of("id", "type", "size"), content.get(0).propertyStream().map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(artifact.getId().toString(), text(content.get(0), "id"));
        assertEquals("POSTER", text(content.get(0), "type"));
        assertEquals(bytes.length, integer(content.get(0), "size"));
        var download = http.perform(get(archivePath + "/" + artifact.getId())).andExpect(status().isOk())
                .andReturn().getResponse();
        assertArrayEquals(bytes, download.getContentAsByteArray());
        assertEquals("application/octet-stream", download.getContentType());
        assertEquals("attachment; filename=\"artifact-" + artifact.getId() + "\"", download.getHeader("Content-Disposition"));
        perform(get(path()), null, null, 401);
        perform(get(path() + "/" + id), tokens.get(7), null, 403);
        var lookup = perform(get("/api/users/students").param("q", "Noah"), tokens.get(1), null, 200);
        assertFalse(array(lookup, "content").isEmpty());
        for (var student : array(lookup, "content")) assertEquals(Set.of("id", "displayName"),
                student.propertyStream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void G6_requiredNestedJsonTypesAndNullableVersusOmittedActionFields() throws Exception {
        var group = create(1);
        String id = text(group, "id");
        assertEquals(Set.of("id", "periodId", "status", "revision", "locked", "invited", "manager", "members", "preferences"),
                group.propertyStream().map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
        assertEquals(1, integer(group, "revision"));
        for (String key : List.of("locked", "invited", "manager")) assertTrue(group.get(key).isBoolean());
        var members = array(group, "members");
        assertEquals(1, members.size());
        var member = members.get(0);
        assertEquals(Set.of("studentId", "displayName", "creator", "self", "approved"), member.propertyStream()
                .map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()));
        UUID.fromString(text(member, "studentId"));
        text(member, "displayName");
        for (String key : List.of("creator", "self", "approved")) assertTrue(member.get(key).isBoolean());
        assertTrue(array(group, "preferences").isEmpty());
        var body = store.object().put("revision", revision(id)).putNull("studentId").putNull("projectIds").putNull("confirm");
        var explicitNull = perform(post(path() + "/" + id + "/actions/APPROVE"), tokens.get(1), body, 200);
        var omitted = change(1, id, "APPROVE", revision(id), Map.of(), 200);
        assertEquals(explicitNull, omitted);
        assertTrue(array(omitted, "members").get(0).get("approved").isBoolean());
        assertTrue(array(omitted, "members").get(0).get("approved").booleanValue());
        String before = stored(id).getPayload();
        perform(post(path() + "/" + id + "/actions/APPROVE"), tokens.get(1), store.object().putNull("revision"), 400);
        perform(post(path() + "/" + id + "/actions/UNKNOWN"), tokens.get(1), Map.of("revision", revision(id)), 400);
        assertEquals(before, stored(id).getPayload());
    }

    @Test
    void G2_concurrentEditConsentLeaveSubmitAndDeadlineClosureAreAtomic() throws Exception {
        for (String competing : List.of("RANK", "LEAVE", "APPROVE")) {
            var p = store.get("application_period", period);
            period = store.create("application_period", null, null, "OPEN", store.parse(p)).getId();
            String firstSolo = text(solo(1), "id");
            String secondSolo = text(solo(2), "id");
            String id = approvedUnit(List.of(1, 2));
            long current = revision(id);
            var ready = new CountDownLatch(1);
            int submission;
            int edited;
            try (var pool = Executors.newFixedThreadPool(2)) {
                var edit = pool.submit(() -> {
                    ready.await();
                    return "LEAVE".equals(competing) ? requestStatus(1, id, "SUBMIT", current, Map.of("confirm", true))
                            : requestStatus(1, id, "RANK", current,
                                    Map.of("projectIds", List.of(projects.get(1), projects.getFirst(), projects.get(2))));
                });
                var other = pool.submit(() -> {
                    ready.await();
                    return "APPROVE".equals(competing) ? requestStatus(2, id, "APPROVE", current, Map.of())
                            : "LEAVE".equals(competing) ? requestStatus(2, id, "LEAVE", current, Map.of())
                            : requestStatus(1, id, "SUBMIT", current, Map.of("confirm", true));
                });
                ready.countDown();
                edited = edit.get();
                submission = other.get();
                assertTrue(edited == 200 || edited == 409);
                assertTrue(submission == 200 || submission == 409);
                if (!"LEAVE".equals(competing)) assertEquals(200, edited);
                assertTrue(edited == 200 || submission == 200);
            }
            var saved = store.parse(stored(id));
            assertEquals("DRAFT", stored(id).getStatus());
            assertTrue(saved.get("approvals").isObject());
            assertTrue(saved.get("approvals").isEmpty());
            boolean replaced = "RANK".equals(competing) && submission == 200 || "LEAVE".equals(competing) && edited == 200;
            String expected = replaced ? "REPLACED" : "SUBMITTED";
            assertSoloStatus(firstSolo, expected);
            assertSoloStatus(secondSolo, expected);
        }
        var p = store.get("application_period", period);
        period = store.create("application_period", null, null, "OPEN", store.parse(p)).getId();
        String firstSolo = text(solo(1), "id");
        String secondSolo = text(solo(2), "id");
        String id = approvedUnit(List.of(1, 2));
        long current = revision(id);
        var closing = store.parse(store.get("application_period", period)).put("closesAt", Instant.now().toString());
        var ready = new CountDownLatch(1);
        int submitted;
        try (var pool = Executors.newFixedThreadPool(2)) {
            var close = pool.submit(() -> {
                ready.await();
                return http.perform(put("/api/application-periods/" + period).header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(closing)))
                        .andReturn().getResponse().getStatus();
            });
            var submit = pool.submit(() -> {
                ready.await(); return requestStatus(1, id, "SUBMIT", current, Map.of("confirm", true));
            });
            ready.countDown();
            assertEquals(200, close.get());
            submitted = submit.get();
            assertTrue(submitted == 200 || submitted == 409);
        }
        String expected = submitted == 200 ? "REPLACED" : "SUBMITTED";
        assertSoloStatus(firstSolo, expected);
        assertSoloStatus(secondSolo, expected);
        assertEquals(submitted == 200 ? "SUBMITTED" : "DRAFT", stored(id).getStatus());
        var deadlineRun = run();
        assertEquals(submitted == 200 ? 1 : 0, array(deadlineRun, "applyingUnits").size());
    }

    @Test
    void G6_laterThanTwentyInboxInvitationsAndStaffUnitsUseRealPagination() throws Exception {
        String creatorGroup = text(create(1), "id");
        String lastInbox = null;
        for (int member = 2; member <= 22; member++) {
            tokens.put(member, login("student%02d".formatted(member)));
            change(1, creatorGroup, "INVITE", revision(creatorGroup), Map.of("studentId", student(member)), 200);
            lastInbox = text(create(member), "id");
            change(member, lastInbox, "INVITE", revision(lastInbox), Map.of("studentId", student(1)), 200);
        }
        var first = perform(get(path()), tokens.get(1), null, 200);
        assertEquals(0, integer(first, "page"));
        assertEquals(20, integer(first, "size"));
        assertEquals(20, array(first, "content").size());
        assertEquals(2, integer(first, "totalPages"));
        assertEquals(22, integer(first, "totalElements"));
        var later = perform(get(path()).param("page", "1"), tokens.get(1), null, 200);
        assertEquals(20, integer(later, "size"));
        assertEquals(1, integer(later, "page"));
        assertEquals(2, integer(later, "totalPages"));
        String last = lastInbox;
        assertTrue(java.util.stream.StreamSupport.stream(array(later, "content").spliterator(), false)
                .anyMatch(group -> text(group, "id").equals(last)));
        var oversizedGroups = perform(get(path()).param("size", "101"), tokens.get(1), null, 200);
        assertEquals(0, integer(oversizedGroups, "page"));
        assertEquals(100, integer(oversizedGroups, "size"));
        assertEquals(22, array(oversizedGroups, "content").size());
        assertEquals(1, integer(oversizedGroups, "totalPages"));
        assertEquals(22, integer(oversizedGroups, "totalElements"));

        var firstInvitations = perform(get(path() + "/" + creatorGroup + "/invitations"),
                tokens.get(1), null, 200);
        assertEquals(0, integer(firstInvitations, "page"));
        assertEquals(20, integer(firstInvitations, "size"));
        assertEquals(20, array(firstInvitations, "content").size());
        assertEquals(2, integer(firstInvitations, "totalPages"));
        assertEquals(21, integer(firstInvitations, "totalElements"));
        var invitations = perform(get(path() + "/" + creatorGroup + "/invitations").param("page", "1"),
                tokens.get(1), null, 200);
        assertEquals(20, integer(invitations, "size"));
        assertEquals(1, integer(invitations, "page"));
        assertEquals(1, array(invitations, "content").size());
        assertEquals(2, integer(invitations, "totalPages"));
        assertEquals(student(22), text(array(invitations, "content").get(0), "studentId"));
        var oversizedInvitations = perform(get(path() + "/" + creatorGroup + "/invitations").param("size", "101"),
                tokens.get(1), null, 200);
        assertEquals(0, integer(oversizedInvitations, "page"));
        assertEquals(100, integer(oversizedInvitations, "size"));
        assertEquals(21, array(oversizedInvitations, "content").size());
        assertEquals(1, integer(oversizedInvitations, "totalPages"));
        assertEquals(21, integer(oversizedInvitations, "totalElements"));
        var staff = perform(get(path()).param("page", "1"), teacher, null, 200);
        assertEquals(2, array(staff, "content").size());
        assertEquals(22, integer(staff, "totalElements"));
    }

    @Test
    void G5_roleChangeBlocksInvalidSubmissionAndExcludesWholeSubmittedUnit() throws Exception {
        String id = approvedUnit(List.of(1, 2));
        var account = users.findByUsername("student02").orElseThrow();
        try {
            account.setRole(ac.at.tgm.submit.security.Role.TEACHER);
            users.saveAndFlush(account);
            rejectedUnchanged(1, id, "SUBMIT", Map.of("confirm", true), 400);
            account.setRole(ac.at.tgm.submit.security.Role.STUDENT);
            users.saveAndFlush(account);
            change(1, id, "SUBMIT", revision(id), Map.of("confirm", true), 200);
            var earlier = run();
            String bytes = stored(text(earlier, "id")).getPayload();
            account.setRole(ac.at.tgm.submit.security.Role.TEACHER);
            users.saveAndFlush(account);
            var invalid = run();
            assertEquals("COMPLETED", text(invalid, "status"));
            assertEquals(1, array(invalid, "excludedApplyingUnits").size());
            assertEquals(id, text(array(invalid, "excludedApplyingUnits").get(0), "groupId"));
            assertEquals(0, array(object(invalid, "result"), "assignments").size());
            assertTrue(invalid.has("result"));
            assertEquals(bytes, stored(text(earlier, "id")).getPayload());
        } finally {
            account.setRole(ac.at.tgm.submit.security.Role.STUDENT);
            users.saveAndFlush(account);
        }
    }

    @Test
    void mixedEligibilityBlocksGroupChangesExcludesWholeSubmittedUnitAndRestoresItUnchanged() throws Exception {
        JsonNode group = create(1);
        String groupId = text(group, "id");
        change(1, groupId, "INVITE", revision(groupId), Map.of("studentId", student(2)), 200);
        change(2, groupId, "ACCEPT", revision(groupId), Map.of(), 200);

        updateEligibility(1, 3, 4, 5, 6, 7);
        change(1, groupId, "RANK", revision(groupId), Map.of("projectIds", projects), 409);

        updateEligibility(1, 2, 3, 4, 5, 6, 7);
        change(1, groupId, "RANK", revision(groupId), Map.of("projectIds", projects), 200);
        change(1, groupId, "APPROVE", revision(groupId), Map.of(), 200);
        change(2, groupId, "APPROVE", revision(groupId), Map.of(), 200);
        updateEligibility(1, 3, 4, 5, 6, 7);
        change(1, groupId, "SUBMIT", revision(groupId), Map.of("confirm", true), 409);

        updateEligibility(1, 2, 3, 4, 5, 6, 7);
        change(1, groupId, "SUBMIT", revision(groupId), Map.of("confirm", true), 200);
        solo(3);
        String savedGroup = stored(groupId).getPayload();
        JsonNode savedRoster = array(store.parse(stored(groupId)), "members");
        List<String> savedStudentIds = new ArrayList<>();
        for (JsonNode savedStudentId : savedRoster) {
            assertTrue(savedStudentId.isTextual(), "Stored group member IDs must be textual UUIDs.");
            UUID.fromString(savedStudentId.textValue());
            savedStudentIds.add(savedStudentId.textValue());
        }
        assertEquals(2, savedRoster.size());
        assertEquals(2, Set.copyOf(savedStudentIds).size(), "Stored group member IDs must be unique.");
        assertEquals(Set.of(student(1), student(2)), Set.copyOf(savedStudentIds));
        updateEligibility(1, 3, 4, 5, 6, 7);
        JsonNode excludedRun = perform(post("/api/application-periods/" + period + "/allocation-runs"),
                admin, null, 201);
        String excludedRunBytes = stored(text(excludedRun, "id")).getPayload();
        JsonNode excludedUnits = array(excludedRun, "excludedApplyingUnits");
        assertEquals(1, excludedUnits.size());
        assertEquals(groupId, text(excludedUnits.get(0), "groupId"));
        assertEquals("MEMBER_INELIGIBLE", text(excludedUnits.get(0), "reason"));
        List<String> excludedRunStudents = assignedStudents(excludedRun);
        assertTrue(excludedRunStudents.contains(student(3)));
        assertFalse(excludedRunStudents.contains(student(1)));
        assertFalse(excludedRunStudents.contains(student(2)));
        assertEquals("SUBMITTED", stored(groupId).getStatus());
        assertEquals(savedGroup, stored(groupId).getPayload());

        updateEligibility(1, 2, 3, 4, 5, 6, 7);
        JsonNode restoredRun = perform(post("/api/application-periods/" + period + "/allocation-runs"),
                admin, null, 201);
        JsonNode restoredGroupUnit = null;
        int matchingGroupUnits = 0;
        for (JsonNode applyingUnit : array(restoredRun, "applyingUnits")) {
            assertTrue(applyingUnit.isObject());
            JsonNode applyingGroupId = applyingUnit.get("groupId");
            if (applyingGroupId != null) {
                assertTrue(applyingGroupId.isTextual());
                if (groupId.equals(applyingGroupId.textValue())) {
                    restoredGroupUnit = applyingUnit;
                    matchingGroupUnits++;
                }
            }
        }
        assertEquals(1, matchingGroupUnits, "The restored run must contain the submitted unit exactly once.");
        assertNotNull(restoredGroupUnit, "The exact submitted group must be present in the restored run.");
        JsonNode restoredStudentIds = array(restoredGroupUnit, "studentIds");
        List<String> restoredMemberIds = new ArrayList<>();
        for (JsonNode restoredStudentId : restoredStudentIds) {
            assertTrue(restoredStudentId.isTextual(), "Applying-unit student IDs must be textual UUIDs.");
            UUID.fromString(restoredStudentId.textValue());
            restoredMemberIds.add(restoredStudentId.textValue());
        }
        assertEquals(savedStudentIds.size(), restoredStudentIds.size());
        assertEquals(Set.copyOf(savedStudentIds), Set.copyOf(restoredMemberIds));
        List<String> restoredRunStudents = assignedStudents(restoredRun);
        assertTrue(restoredRunStudents.contains(student(1)));
        assertTrue(restoredRunStudents.contains(student(2)));
        JsonNode firstMemberAssignment = assignmentForStudent(restoredRun, student(1));
        JsonNode secondMemberAssignment = assignmentForStudent(restoredRun, student(2));
        assertEquals(text(firstMemberAssignment, "projectId"), text(secondMemberAssignment, "projectId"));
        assertTrue(firstMemberAssignment.path("hadPreferences").isBoolean());
        assertTrue(firstMemberAssignment.path("hadPreferences").asBoolean());
        assertTrue(secondMemberAssignment.path("hadPreferences").isBoolean());
        assertTrue(secondMemberAssignment.path("hadPreferences").asBoolean());
        assertEquals("SUBMITTED", stored(groupId).getStatus());
        assertEquals(savedGroup, stored(groupId).getPayload());
        assertEquals(excludedRunBytes, stored(text(excludedRun, "id")).getPayload());
    }

    @Test
    void releasedReplacedSoloApplicationDoesNotHideEligibleCallersOwnAssignment() throws Exception {
        JsonNode soloApplication = solo(1);
        String replacedSoloId = text(soloApplication, "id");
        String savedSoloPayload = stored(replacedSoloId).getPayload();
        JsonNode savedSoloPreferences = array(store.parse(stored(replacedSoloId)), "preferences").deepCopy();
        assertFalse(savedSoloPreferences.isEmpty(), "The replaced solo application must have saved preferences.");
        String groupId = text(create(1), "id");
        change(1, groupId, "INVITE", revision(groupId), Map.of("studentId", student(2)), 200);
        change(2, groupId, "ACCEPT", revision(groupId), Map.of(), 200);
        updateEligibility(1, 2, 3, 4, 5, 6, 7);
        change(1, groupId, "RANK", revision(groupId), Map.of("projectIds", projects), 200);
        change(1, groupId, "APPROVE", revision(groupId), Map.of(), 200);
        change(2, groupId, "APPROVE", revision(groupId), Map.of(), 200);
        change(1, groupId, "SUBMIT", revision(groupId), Map.of("confirm", true), 200);
        assertEquals("REPLACED", stored(replacedSoloId).getStatus());

        change(2, groupId, "LEAVE", revision(groupId), Map.of(), 200);
        assertEquals("DRAFT", stored(groupId).getStatus());
        assertReplacedSoloUnchanged(replacedSoloId, savedSoloPayload, savedSoloPreferences);
        updateEligibility(1, 3, 4);
        closePeriodThroughApi();
        JsonNode run = run();
        assertReplacedSoloUnchanged(replacedSoloId, savedSoloPayload, savedSoloPreferences);
        JsonNode staffAssignment = assignmentForStudent(run, student(1));
        assertTrue(staffAssignment.get("hadPreferences").isBoolean());
        assertFalse(staffAssignment.get("hadPreferences").booleanValue());

        JsonNode ownPeriod = perform(get("/api/application-periods/" + period), tokens.get(1), null, 200);
        assertEquals("NOT_SUBMITTED", text(ownPeriod, "studentApplicationStatus"));
        JsonNode ownAssignment = object(ownPeriod, "studentAssignment");
        assertEquals("PRELIMINARY", text(ownAssignment, "state"));
        assertTrue(ownAssignment.get("assignedWithoutPreferences").isBoolean());
        assertTrue(ownAssignment.get("assignedWithoutPreferences").booleanValue());
        assertFalse(ownAssignment.has("hadPreferences"));
        assertFalse(ownAssignment.hasNonNull("preferenceRank"),
                "A replaced solo ranking must not appear as this run's preference rank.");
        assertFalse(ownAssignment.has("preferences"));
        assertFalse(ownAssignment.toString().contains("rankings"));
        assertFalse(ownAssignment.toString().contains("eligibleStudentIds"));
        assertFalse(ownAssignment.toString().contains("class"));
        JsonNode assignedProject = store.parse(store.get("idea",
                UUID.fromString(text(staffAssignment, "projectId"))));
        assertEquals(text(assignedProject, "title"), text(ownAssignment, "projectTitle"));
        for (JsonNode member : array(ownAssignment, "groupMembers")) {
            assertTrue(member.isObject());
            assertFalse(member.has("hadPreferences"));
            assertFalse(member.has("assignedWithoutPreferences"));
        }
        assertReplacedSoloUnchanged(replacedSoloId, savedSoloPayload, savedSoloPreferences);
    }

    private void assertReplacedSoloUnchanged(String applicationId, String savedPayload, JsonNode savedPreferences) {
        StoredRecord application = stored(applicationId);
        assertEquals("REPLACED", application.getStatus());
        assertEquals(savedPayload, application.getPayload());
        assertEquals(savedPreferences, array(store.parse(application), "preferences"));
    }

    private void updateEligibility(int... studentNumbers) throws Exception {
        var periodData = store.parse(store.get("application_period", period));
        periodData.put("eligibilityMode", "SELECTED");
        List<String> selectedIds = java.util.Arrays.stream(studentNumbers).mapToObj(this::student).toList();
        periodData.set("eligibleStudentIds", store.value(selectedIds));
        periodData.putArray("eligibleClassIds");
        perform(put("/api/application-periods/" + period), admin, periodData, 200);
    }

    private void closePeriodThroughApi() throws Exception {
        var periodData = store.parse(store.get("application_period", period));
        periodData.put("closesAt", Instant.now().minusSeconds(60).toString());
        perform(put("/api/application-periods/" + period), admin, periodData, 200);
    }

    private List<String> assignedStudents(JsonNode run) {
        List<String> result = new ArrayList<>();
        for (JsonNode assignment : array(object(run, "result"), "assignments")) {
            result.add(text(assignment, "studentId"));
        }
        return result;
    }

    private JsonNode assignmentForStudent(JsonNode run, String studentId) {
        for (JsonNode assignment : array(object(run, "result"), "assignments")) {
            if (studentId.equals(text(assignment, "studentId"))) {
                return assignment;
            }
        }
        throw new AssertionError("No assignment found for " + studentId);
    }

    private int requestStatus(int student, String id, String action, long revision, Map<String, Object> fields) throws Exception {
        var body = new HashMap<>(fields);
        body.put("revision", revision);
        return http.perform(post(path() + "/" + id + "/actions/" + action)
                .header("Authorization", "Bearer " + tokens.get(student)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andReturn().getResponse().getStatus();
    }

    private JsonNode array(JsonNode object, String key) {
        assertNotNull(object);
        assertNotNull(object.get(key), key + " must be present");
        assertTrue(object.get(key).isArray(), key + " must be an array");
        return object.get(key);
    }

    private JsonNode object(JsonNode parent, String key) {
        assertNotNull(parent);
        assertNotNull(parent.get(key), key + " must be present");
        assertTrue(parent.get(key).isObject(), key + " must be an object");
        return parent.get(key);
    }

    private boolean bool(JsonNode parent, String key) {
        assertNotNull(parent);
        assertNotNull(parent.get(key), key + " must be present");
        assertTrue(parent.get(key).isBoolean(), key + " must be a boolean");
        return parent.get(key).booleanValue();
    }

    private com.fasterxml.jackson.databind.node.ObjectNode formalBody(List<Integer> members) {
        var body = store.object().put("topic", "Shared final group topic")
                .put("initialSituation", "Synthetic final group")
                .put("objective", "Check complete final membership").put("cooperationPartner", "");
        var topics = body.putArray("individualTopics");
        var concerns = body.putArray("investigationConcerns");
        var results = body.putArray("plannedResults");
        for (int member : members) {
            topics.addObject().put("studentId", student(member)).put("topic", "Individual topic");
            concerns.addObject().put("studentId", student(member)).put("text", "Investigation concern");
            results.addObject().put("studentId", student(member)).put("text", "Planned result");
        }
        body.putArray("milestones").addObject().put("text", "Complete result").put("date", "2027-02-01");
        return body;
    }

    private String pinBytes() {
        return store.repository().findByTypeAndParentId("allocation_pin", period.toString()).stream()
                .sorted(java.util.Comparator.comparing(record -> record.getId().toString()))
                .map(record -> record.getId() + record.getPayload()).collect(java.util.stream.Collectors.joining("|"));
    }

    private void rejectedUnchanged(int student, String id, String action, Map<String, Object> fields, int expected) throws Exception {
        String before = stored(id).getPayload();
        String status = stored(id).getStatus();
        int audits = store.repository().findByTypeAndParentId("audit", id).size();
        change(student, id, action, revision(id), fields, expected);
        assertEquals(before, stored(id).getPayload());
        assertEquals(status, stored(id).getStatus());
        assertEquals(audits, store.repository().findByTypeAndParentId("audit", id).size());
    }

    private String submittedPair() throws Exception {
        return submittedUnit(List.of(1, 2));
    }

    private String submittedUnit(List<Integer> members) throws Exception {
        String id = approvedUnit(members);
        change(members.getFirst(), id, "SUBMIT", revision(id), Map.of("confirm", true), 200);
        return id;
    }

    private String approvedUnit(List<Integer> members) throws Exception {
        int creator = members.getFirst();
        String id = text(create(creator), "id");
        for (int member : members.subList(1, members.size())) {
            change(creator, id, "INVITE", revision(id), Map.of("studentId", student(member)), 200);
            change(member, id, "ACCEPT", revision(id), Map.of(), 200);
        }
        change(creator, id, "RANK", revision(id), Map.of("projectIds", projects), 200);
        for (int member : members) change(member, id, "APPROVE", revision(id), Map.of(), 200);
        return id;
    }

    private JsonNode run() throws Exception {
        return perform(post("/api/application-periods/" + period + "/allocation-runs"), admin, null, 201);
    }

    private JsonNode create(int student) throws Exception {
        includeEligible(student);
        return perform(post(path()), tokens.get(student), null, 201);
    }

    private JsonNode solo(int student) throws Exception {
        includeEligible(student);
        return perform(post("/api/application-periods/" + period + "/applications"), tokens.get(student),
                Map.of("projectIds", projects), 201);
    }

    private JsonNode change(int student, String id, String action, long revision, Map<String, Object> fields, int expected)
            throws Exception {
        Object target = fields.get("studentId");
        if (target != null) includeEligible(target.toString());
        Map<String, Object> body = new HashMap<>(fields);
        body.put("revision", revision);
        return perform(post(path() + "/" + id + "/actions/" + action), tokens.get(student), body, expected);
    }

    private int actionStatus(int student, String id, String action, long revision) throws Exception {
        return http.perform(post(path() + "/" + id + "/actions/" + action).header("Authorization", "Bearer " + tokens.get(student))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("revision", revision))))
                .andReturn().getResponse().getStatus();
    }

    private String path() { return "/api/application-periods/" + period + "/applying-groups"; }
    private String student(int number) { return users.findByUsername("student%02d".formatted(number)).orElseThrow().getId().toString(); }

    private void includeEligible(int student) {
        includeEligible(student(student));
    }

    private void includeEligible(String studentId) {
        var periodRecord = store.get("application_period", period);
        var data = store.parse(periodRecord);
        var ids = data.withArray("eligibleStudentIds");
        boolean present = false;
        for (JsonNode id : ids) present |= studentId.equals(id.asText());
        if (!present) {
            ids.add(studentId);
            store.update(periodRecord, periodRecord.getStatus(), data);
        }
    }

    private StoredRecord stored(String id) { return store.repository().findById(UUID.fromString(id)).orElseThrow(); }
    private long revision(String id) { return integer(store.parse(stored(id)), "revision"); }
    private void assertSoloStatus(String id, String expected) { assertEquals(expected, stored(id).getStatus()); }

    private String login(String username) throws Exception {
        return text(perform(post("/api/auth/login"), null, Map.of("username", username, "password", "testing-password"), 200), "token");
    }

    private void setRoles(String id, List<String> roles) throws Exception {
        perform(patch("/api/users/" + id + "/role"), admin, Map.of("roles", roles), 200);
    }

    private long auditCount() {
        return store.repository().findByType("audit", org.springframework.data.domain.PageRequest.of(0, 1000))
                .getTotalElements();
    }

    private JsonNode perform(MockHttpServletRequestBuilder request, String token, Object body, int expected) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = http.perform(request).andExpect(status().is(expected)).andReturn().getResponse();
        return response.getContentAsString().isBlank() ? json.nullNode() : json.readTree(response.getContentAsString());
    }

    private String text(JsonNode node, String key) {
        assertNotNull(node, "Object containing " + key + " must be present");
        assertTrue(node.hasNonNull(key) && node.get(key).isTextual(), key + " must be a present string");
        return node.get(key).textValue();
    }

    private int integer(JsonNode node, String key) {
        assertNotNull(node, "Object containing " + key + " must be present");
        assertTrue(node.hasNonNull(key) && node.get(key).isIntegralNumber(), key + " must be a present integer");
        return node.get(key).intValue();
    }
}
