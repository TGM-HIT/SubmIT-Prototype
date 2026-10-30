package at.ac.tgm.submit.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import at.ac.tgm.submit.allocation.AllocationOptimizer.Application;
import at.ac.tgm.submit.allocation.AllocationOptimizer.ProjectUnassignedReason;
import at.ac.tgm.submit.allocation.AllocationOptimizer.Request;
import at.ac.tgm.submit.allocation.AllocationOptimizer.Result;
import at.ac.tgm.submit.allocation.AllocationOptimizer.StudentUnassignedReason;
import at.ac.tgm.submit.allocation.AllocationOptimizer.UnassignedStudent;
import at.ac.tgm.submit.allocation.AllocationOptimizer.Assignment;
import at.ac.tgm.submit.allocation.AllocationOptimizer.Project;
import org.junit.jupiter.api.Test;

class AllocationOptimizerTest {

    private final AllocationOptimizer optimizer = new AllocationOptimizer();

    private static Application student(String id, String... ranking) {
        return new Application(id, List.of(ranking));
    }

    private static List<Project> projects(String... ids) {
        return java.util.Arrays.stream(ids).map(Project::new).toList();
    }

    private static long groupCount(Result result) {
        return result.assignments().stream().map(Assignment::projectId).distinct().count();
    }

    private static List<Application> students(String prefix, int count, String... ranking) {
        return IntStream.range(0, count).mapToObj(i -> student(prefix + i, ranking)).toList();
    }

    private static List<Application> concat(List<Application> a, List<Application> b) {
        List<Application> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    @Test
    void formsSingleGroupAndReportsUnusedProject() {
        Result result = optimizer.optimize(new Request(
                projects("A", "B"), students("s", 4, "A", "B")));

        assertEquals(4, result.assignedStudents());
        assertEquals(1L, groupCount(result));
        assertEquals("A", result.assignments().get(0).projectId());
        assertEquals(1, result.assignments().get(0).preferenceRank());
        assertEquals(ProjectUnassignedReason.NOT_SELECTED_BY_OPTIMIZATION, result.unassignedProjects().get(0).reason());
        assertEquals(4, result.unassignedProjects().get(0).interestedStudents());
    }

    @Test
    void prioritizesAssignedCountOverPreference() {
        // Six students prefer A but it seats five, so one takes the second choice to assign all nine.
        List<Application> all = concat(students("a", 6, "A", "B"), students("b", 3, "B", "A"));
        Result result = optimizer.optimize(new Request(projects("A", "B"), all));

        assertEquals(9, result.assignedStudents());
        assertTrue(result.unassignedStudents().isEmpty());
    }

    @Test
    void prefersHigherRankedChoicesWhenCountIsEqual() {
        List<Application> all = concat(students("a", 3, "A", "B"), students("b", 3, "B", "A"));
        Result result = optimizer.optimize(new Request(projects("A", "B"), all));

        assertEquals(6, result.assignedStudents());
        assertEquals(12, result.preferenceScore());
        assertEquals(2L, groupCount(result));
    }

    @Test
    void allocatesEligibleStudentsWithoutPreferencesToFeasibleCatalogueProjectsAtZeroSatisfaction() {
        List<Application> eligible = IntStream.range(0, 3)
                .mapToObj(index -> new Application("eligible-" + index, List.of(), null, false)).toList();
        Result result = optimizer.optimize(new Request(projects("catalogue-A", "catalogue-B", "catalogue-C"), eligible));

        assertEquals(3, result.assignedStudents());
        assertEquals(0, result.preferenceScore());
        assertEquals(1, result.assignments().stream().map(Assignment::projectId).distinct().count());
        assertTrue(Set.of("catalogue-A", "catalogue-B", "catalogue-C")
                .contains(result.assignments().getFirst().projectId()));
        assertTrue(result.assignments().stream().allMatch(assignment -> assignment.preferenceRank() == 0));
    }

    @Test
    void maximizesAssignedCountWithMixedRankedAndNoPreferenceCandidatesBeforePreferenceScore() {
        List<Application> ranked = students("ranked-", 5, "A", "B");
        List<Application> withoutPreferences = IntStream.range(0, 1)
                .mapToObj(index -> new Application("unranked-" + index, List.of(), null, false)).toList();
        Result result = optimizer.optimize(new Request(projects("A", "B"), concat(ranked, withoutPreferences)));

        assertEquals(6, result.assignedStudents(),
                "Assigning all six at score 8 must beat the five-ranked score-10 solution.");
        assertEquals(2, groupCount(result));
        assertTrue(result.assignments().stream().collect(java.util.stream.Collectors.groupingBy(
                Assignment::projectId, java.util.stream.Collectors.counting())).values().stream()
                .allMatch(size -> size >= 3 && size <= 5));
        assertEquals(1, result.assignments().stream().filter(assignment -> assignment.preferenceRank() == 0).count());
        assertEquals(5, result.assignments().stream().filter(assignment -> assignment.preferenceRank() > 0).count());
        assertEquals(8, result.preferenceScore());
        assertEquals(0, result.unassignedStudents().size());
    }

    @Test
    void fewerThanThreeNoPreferenceCandidatesNeverFormAnUndersizedGroup() {
        List<Application> unranked = IntStream.range(0, 2)
                .mapToObj(index -> new Application("unranked-" + index, List.of(), null, false)).toList();
        Result result = optimizer.optimize(new Request(projects("catalogue-A"), unranked));

        assertTrue(result.assignments().isEmpty());
        assertEquals(0, result.assignedStudents());
        assertEquals(0, result.preferenceScore());
        assertEquals(2, result.unassignedStudents().size());
        assertTrue(result.unassignedStudents().stream().allMatch(student -> student.rankedProjectIds().isEmpty()));
    }

    @Test
    void leavesStudentsUnassignedWithReasonAndPreferences() {
        Result result = optimizer.optimize(new Request(
                projects("A", "B"), concat(students("a", 6, "A"), List.of(student("x", "B")))));

        assertEquals(5, result.assignedStudents());
        assertEquals(2, result.unassignedStudents().size());
        UnassignedStudent full = result.unassignedStudents().get(0);
        assertEquals(StudentUnassignedReason.RANKED_GROUPS_FULL, full.reason());
        assertEquals(List.of("A"), full.rankedProjectIds());
        UnassignedStudent lonely = result.unassignedStudents().get(1);
        assertEquals(StudentUnassignedReason.RANKED_PROJECTS_NOT_FORMED, lonely.reason());
        assertEquals(ProjectUnassignedReason.INSUFFICIENT_INTEREST, result.unassignedProjects().get(0).reason());
    }

    @Test
    void classifiesUnassignedStudentsByFormedRankedGroups() {
        assertEquals(StudentUnassignedReason.RANKED_PROJECTS_NOT_FORMED, AllocationOptimizer.studentReason(List.of()));
        assertEquals(StudentUnassignedReason.RANKED_GROUPS_FULL, AllocationOptimizer.studentReason(List.of(5, 5)));
        assertEquals(StudentUnassignedReason.NOT_SELECTED_BY_OPTIMIZATION, AllocationOptimizer.studentReason(List.of(5, 4)));
        assertEquals(StudentUnassignedReason.NOT_SELECTED_BY_OPTIMIZATION, AllocationOptimizer.studentReason(List.of(3)));
    }

    @Test
    void neverAssignsTwoOrFewerStudents() {
        Result result = optimizer.optimize(new Request(
                projects("A"), students("s", 2, "A")));

        assertTrue(result.assignments().isEmpty());
        assertEquals(2, result.unassignedStudents().size());
    }

    @Test
    void isDeterministicAndRespectsConstraintsOnRandomInputs() {
        Random random = new Random(42);
        for (int round = 0; round < 20; round++) {
            Request request = randomRequest(random, 20 + random.nextInt(20), 6);
            Result first = optimizer.optimize(request);
            assertEquals(first, optimizer.optimize(request));
            assertValid(request, first);
        }
    }

    @Test
    void matchesBruteForceOnSmallInputs() {
        Random random = new Random(7);
        for (int round = 0; round < 40; round++) {
            Request request = randomRequest(random, 6 + random.nextInt(4), 3);
            Result result = optimizer.optimize(request);
            long[] best = bruteForce(request);
            assertEquals(best[0], result.assignedStudents());
            assertEquals(best[1], result.preferenceScore());
        }
    }

    @Test
    void seededSixtyStudentTwelveProjectWorkloadRetainsItsOriginalTimeout() {
        Request request = randomRequest(new Random(1), 60, 12);
        Result result = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> optimizer.optimize(request));
        assertValid(request, result);
    }

    @Test
    void preservesCompleteLegacyResultForTiedPinnedAndIndivisibleRequest() {
        List<Application> applications = List.of(
                student("a0", "A"),
                student("a1", "A"),
                student("a2", "A"),
                student("overflow-0", "A"),
                student("overflow-1", "A"),
                student("overflow-2", "A"),
                new Application("unit-0", List.of("A", "B"), "shared"),
                new Application("unit-1", List.of("A", "B"), "shared"),
                student("b0", "B"),
                student("b1", "B"),
                student("b2", "B"),
                new Application("no-preference", List.of(), null, false));
        Request request = new Request(projects("B", "A", "C"), applications, Set.of("a0"));

        Result result = optimizer.optimize(request);

        Result expected = new Result(List.of(
                new Assignment("unit-0", "B", 2, false),
                new Assignment("unit-1", "B", 2, false),
                new Assignment("b0", "B", 1, false),
                new Assignment("b1", "B", 1, false),
                new Assignment("b2", "B", 1, false),
                new Assignment("a0", "A", 1, false),
                new Assignment("a2", "A", 1, false),
                new Assignment("overflow-0", "A", 1, false),
                new Assignment("overflow-1", "A", 1, false),
                new Assignment("overflow-2", "A", 1, false)),
                List.of(
                        new UnassignedStudent("a1", StudentUnassignedReason.RANKED_GROUPS_FULL, List.of("A")),
                        new UnassignedStudent("no-preference",
                                StudentUnassignedReason.RANKED_PROJECTS_NOT_FORMED, List.of())),
                List.of(
                        new AllocationOptimizer.UnassignedProject("C",
                                ProjectUnassignedReason.INSUFFICIENT_INTEREST, 0)),
                10, 28);
        assertEquals(expected, result,
                "Complete ordered result is retained from the pre-change subset traversal for this tied request.");
        assertEquals(expected, optimizer.optimize(request));
    }

    @Test
    void exactSearchAccountsFor113StudentsAnd33Projects() {
        List<String> projectIds = IntStream.range(0, 33).mapToObj(index -> "P" + index).toList();
        List<Project> projects = projectIds.stream().map(Project::new).toList();
        List<Application> applications = new ArrayList<>();
        Set<String> pinned = new HashSet<>();
        for (int projectIndex = 0; projectIndex < projectIds.size(); projectIndex++) {
            String project = projectIds.get(projectIndex);
            if (projectIndex < 7) {
                applications.add(new Application(project + "-unit-a", List.of(project), project + "-unit"));
                applications.add(new Application(project + "-unit-b", List.of(project), project + "-unit"));
                String pinnedSolo = project + "-solo-base";
                applications.add(student(pinnedSolo, project));
                pinned.add(pinnedSolo);
            } else {
                applications.addAll(students(project + "-ranked-", 3, project));
            }
            if (projectIndex >= 7 && projectIndex < 20) {
                applications.add(student(project + "-ranked-extra", project));
            }
        }
        applications.add(new Application("eligible-without-preferences", List.of(), null, false));
        Request request = new Request(projects, applications, pinned);

        Result result = assertTimeoutPreemptively(Duration.ofSeconds(30), () -> optimizer.optimize(request));
        Result repeated = assertTimeoutPreemptively(Duration.ofSeconds(30), () -> optimizer.optimize(request));

        assertEquals(result, repeated);
        assertEquals(113, result.assignedStudents());
        assertExactAccountingAndObjective(request, result);
        assertEquals(Set.copyOf(projectIds), result.assignments().stream()
                .map(Assignment::projectId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(3696, result.preferenceScore());
        assertTrue(result.unassignedStudents().isEmpty());
        assertEquals(1, result.assignments().stream().filter(assignment -> assignment.preferenceRank() == 0).count());
        assertTrue(pinned.stream().allMatch(id -> result.assignments().stream()
                .anyMatch(assignment -> assignment.studentId().equals(id))));
        for (int projectIndex = 0; projectIndex < 7; projectIndex++) {
            String unitPrefix = "P" + projectIndex + "-unit-";
            List<Assignment> unitAssignments = result.assignments().stream()
                    .filter(assignment -> assignment.studentId().startsWith(unitPrefix)).toList();
            assertEquals(2, unitAssignments.size());
            assertEquals(1, unitAssignments.stream().map(Assignment::projectId).distinct().count());
        }
    }

    @Test
    void projectsBeyondLongMaskBoundaryParticipateInExactOptimization() {
        List<String> projectIds = IntStream.range(0, 66).mapToObj(index -> "P" + index).toList();
        List<Application> applications = new ArrayList<>();
        for (String project : projectIds) {
            applications.addAll(students(project + "-ranked-", 3, project));
        }
        Request request = new Request(projectIds.stream().map(Project::new).toList(), applications);

        Result result = optimizer.optimize(request);

        assertEquals(198, result.assignedStudents());
        assertExactAccountingAndObjective(request, result);
        assertEquals(Set.copyOf(projectIds), result.assignments().stream()
                .map(Assignment::projectId).collect(java.util.stream.Collectors.toSet()));
        assertEquals(66L * 3 * 66, result.preferenceScore());
    }

    @Test
    void splitUnitBranchingBeyondTwelveProjectsMatchesKnownObjective() {
        List<String> projectIds = new ArrayList<>(List.of("A", "B"));
        List<Application> applications = new ArrayList<>();
        applications.addAll(students("a-", 4, "A"));
        applications.addAll(students("b-", 3, "B"));
        applications.add(new Application("unit-0", List.of("A", "B"), "shared"));
        applications.add(new Application("unit-1", List.of("A", "B"), "shared"));
        for (int index = 2; index < 13; index++) {
            String project = "P" + index;
            projectIds.add(project);
            applications.addAll(students(project + "-", 3, project));
        }
        Request request = new Request(projectIds.stream().map(Project::new).toList(), applications);

        Result first = optimizer.optimize(request);
        Result repeated = optimizer.optimize(request);

        assertEquals(first, repeated);
        assertExactAccountingAndObjective(request, first);
        assertEquals(42, first.assignedStudents());
        assertEquals(544, first.preferenceScore());
        List<Assignment> unitAssignments = first.assignments().stream()
                .filter(row -> row.studentId().startsWith("unit-")).toList();
        assertEquals(2, unitAssignments.size());
        assertEquals(1, unitAssignments.stream().map(Assignment::projectId).distinct().count());
        assertEquals("B", unitAssignments.getFirst().projectId());
        assertEquals(Set.copyOf(projectIds), first.assignments().stream()
                .map(Assignment::projectId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void undersizedProjectBranchingBeyondTwelveProjectsPreservesPinsAndNoPreferenceAccounting() {
        List<String> projectIds = new ArrayList<>(List.of("A", "D"));
        List<Application> applications = new ArrayList<>();
        applications.addAll(students("a-", 3, "A"));
        applications.addAll(students("flex-", 3, "A", "D"));
        applications.add(new Application("unranked", List.of(), null, false));
        Set<String> pinned = Set.of("a-0");
        for (int index = 2; index < 13; index++) {
            String project = "P" + index;
            projectIds.add(project);
            applications.addAll(students(project + "-", 3, project));
        }
        Request request = new Request(projectIds.stream().map(Project::new).toList(), applications, pinned);

        Result result = optimizer.optimize(request);

        assertExactAccountingAndObjective(request, result);
        assertEquals(40, result.assignedStudents());
        assertEquals(505, result.preferenceScore());
        assertTrue(result.assignments().stream().anyMatch(row -> row.studentId().equals("a-0")
                && row.projectId().equals("A")), "The pinned candidate must remain assigned to A.");
        assertTrue(result.assignments().stream().anyMatch(row -> row.studentId().equals("unranked")
                && row.preferenceRank() == 0));
        assertEquals(3, result.assignments().stream().filter(row -> row.projectId().equals("D")).count());
        assertTrue(result.unassignedProjects().stream().noneMatch(row -> row.projectId().equals("D")));
        assertEquals(Set.copyOf(projectIds), result.assignments().stream()
                .map(Assignment::projectId).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void validatesInputs() {
        assertThrows(IllegalArgumentException.class, () -> student("s", "A", "A"));
        assertThrows(IllegalArgumentException.class, () -> new Application(" ", List.of("A")));
        assertThrows(IllegalArgumentException.class, () -> new Application("s", List.of()));
        assertThrows(NullPointerException.class, () -> new Application("s", null));
        assertThrows(IllegalArgumentException.class, () -> new Request(projects("A", "A"), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new Request(projects("A"), List.of(student("s", "B"))));
        assertThrows(IllegalArgumentException.class,
                () -> new Request(projects("A"), List.of(student("s", "A"), student("s", "A"))));
        assertThrows(NullPointerException.class, () -> optimizer.optimize(null));
    }

    @Test
    void emptyRequestYieldsEmptyResult() {
        Result result = optimizer.optimize(new Request(projects(), List.of()));
        assertEquals(0, result.assignedStudents());
        assertTrue(result.assignments().isEmpty());
    }

    private static Request randomRequest(Random random, int studentCount, int projectCount) {
        List<String> projects = IntStream.range(0, projectCount).mapToObj(i -> "P" + i).toList();
        List<Application> list = new ArrayList<>();
        for (int s = 0; s < studentCount; s++) {
            List<String> shuffled = new ArrayList<>(projects);
            java.util.Collections.shuffle(shuffled, random);
            int length = Math.min(projectCount, 3 + random.nextInt(2));
            list.add(new Application("S" + s, shuffled.subList(0, length)));
        }
        return new Request(projects(projects.toArray(String[]::new)), list);
    }

    private static void assertValid(Request request, Result result) {
        Set<String> seen = new HashSet<>();
        Map<String, Integer> sizes = new HashMap<>();
        for (Assignment assignment : result.assignments()) {
            assertTrue(seen.add(assignment.studentId()));
            sizes.merge(assignment.projectId(), 1, Integer::sum);
        }
        sizes.values().forEach(size -> assertTrue(size >= 3 && size <= 5));
        for (UnassignedStudent unassigned : result.unassignedStudents()) {
            assertTrue(seen.add(unassigned.studentId()));
        }
        assertEquals(request.applications().size(), seen.size());
        assertEquals(request.projects().size(), groupCount(result) + result.unassignedProjects().size());
    }

    private static void assertExactAccountingAndObjective(Request request, Result result) {
        assertValid(request, result);
        Set<String> expectedStudents = request.applications().stream().map(Application::studentId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> expectedProjects = request.projects().stream().map(Project::projectId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> seenStudents = new HashSet<>();
        Map<String, Integer> sizes = new HashMap<>();
        long score = 0;
        for (Assignment assignment : result.assignments()) {
            assertTrue(expectedProjects.contains(assignment.projectId()));
            Application application = request.applications().stream()
                    .filter(row -> row.studentId().equals(assignment.studentId())).findFirst().orElseThrow();
            int expectedRank = application.rankedProjectIds().indexOf(assignment.projectId()) + 1;
            if (application.hasPreferences()) {
                assertTrue(expectedRank > 0, "Ranked candidates must be assigned only to ranked projects.");
                assertEquals(expectedRank, assignment.preferenceRank());
                score += request.projects().size() - expectedRank + 1L;
            } else {
                assertEquals(0, assignment.preferenceRank());
            }
            assertTrue(seenStudents.add(assignment.studentId()), "Each candidate is accounted for once.");
            sizes.merge(assignment.projectId(), 1, Integer::sum);
        }
        for (UnassignedStudent unassigned : result.unassignedStudents()) {
            assertTrue(expectedStudents.contains(unassigned.studentId()));
            assertTrue(seenStudents.add(unassigned.studentId()), "Unassigned candidates are unique.");
            assertTrue(unassigned.reason() != null);
        }
        assertEquals(expectedStudents, seenStudents);
        Set<String> accountedProjects = java.util.stream.Stream.concat(
                sizes.keySet().stream(), result.unassignedProjects().stream()
                        .map(AllocationOptimizer.UnassignedProject::projectId))
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(expectedProjects, accountedProjects);
        assertEquals(result.assignments().size(), result.assignedStudents());
        assertEquals(score, result.preferenceScore());
        sizes.values().forEach(size -> assertTrue(size >= 3 && size <= 5));
        for (String unitId : request.applications().stream().map(Application::applyingUnitId)
                .filter(java.util.Objects::nonNull).distinct().toList()) {
            List<String> unitMembers = request.applications().stream()
                    .filter(application -> unitId.equals(application.applyingUnitId()))
                    .map(Application::studentId).toList();
            List<Assignment> assignedMembers = result.assignments().stream()
                    .filter(assignment -> unitMembers.contains(assignment.studentId())).toList();
            assertTrue(assignedMembers.isEmpty() || assignedMembers.size() == unitMembers.size());
            if (!assignedMembers.isEmpty()) {
                assertEquals(1, assignedMembers.stream().map(Assignment::projectId).distinct().count());
            }
        }
    }

    /** Returns {assigned, score} of the best allocation by exhaustive search. */
    private static long[] bruteForce(Request request) {
        int projects = request.projects().size();
        int n = request.applications().size();
        long[] best = {0, 0};
        int[] choice = new int[n];
        search(request, 0, choice, projects, best);
        return best;
    }

    private static void search(Request request, int index, int[] choice, int projects, long[] best) {
        if (index == choice.length) {
            evaluate(request, choice, projects, best);
            return;
        }
        for (int p = -1; p < projects; p++) {
            choice[index] = p;
            if (p < 0 || request.applications().get(index).rankedProjectIds().contains(request.projects().get(p).projectId())) {
                search(request, index + 1, choice, projects, best);
            }
        }
    }

    private static void evaluate(Request request, int[] choice, int projects, long[] best) {
        int[] sizes = new int[projects];
        long count = 0;
        long score = 0;
        for (int s = 0; s < choice.length; s++) {
            if (choice[s] >= 0) {
                sizes[choice[s]]++;
                count++;
                score += projects - request.applications().get(s).rankedProjectIds().indexOf(request.projects().get(choice[s]).projectId());
            }
        }
        for (int size : sizes) {
            if (size != 0 && (size < 3 || size > 5)) {
                return;
            }
        }
        if (count > best[0] || count == best[0] && score > best[1]) {
            best[0] = count;
            best[1] = score;
        }
    }
}
