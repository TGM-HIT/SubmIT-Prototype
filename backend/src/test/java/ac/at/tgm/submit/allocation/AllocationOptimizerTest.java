package ac.at.tgm.submit.allocation;

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
import ac.at.tgm.submit.allocation.AllocationOptimizer.Application;
import ac.at.tgm.submit.allocation.AllocationOptimizer.ProjectUnassignedReason;
import ac.at.tgm.submit.allocation.AllocationOptimizer.Request;
import ac.at.tgm.submit.allocation.AllocationOptimizer.Result;
import ac.at.tgm.submit.allocation.AllocationOptimizer.StudentUnassignedReason;
import ac.at.tgm.submit.allocation.AllocationOptimizer.UnassignedStudent;
import ac.at.tgm.submit.allocation.AllocationOptimizer.Assignment;
import ac.at.tgm.submit.allocation.AllocationOptimizer.Project;
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
    void largestSupportedRequestFinishesInReasonableTime() {
        Request request = randomRequest(new Random(1), AllocationLimits.MAX_STUDENTS, AllocationLimits.MAX_PROJECTS);
        Result result = assertTimeoutPreemptively(Duration.ofSeconds(60), () -> optimizer.optimize(request));
        assertValid(request, result);
    }

    @Test
    void rejectsExcessiveInputExplicitly() {
        List<Application> tooMany = students("s", AllocationLimits.MAX_STUDENTS + 1, "A");
        assertThrows(AllocationLimitExceededException.class,
                () -> optimizer.optimize(new Request(projects("A"), tooMany)));
        List<String> manyProjects = IntStream.range(0, AllocationLimits.MAX_PROJECTS + 1).mapToObj(i -> "P" + i).toList();
        assertThrows(AllocationLimitExceededException.class,
                () -> optimizer.optimize(new Request(projects(manyProjects.toArray(String[]::new)), List.of())));
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
