package ac.at.tgm.submit.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Test-owned exhaustive feasible-set oracle: no flow, production profit weights or production unit builder. */
class ApplyingUnitOptimalityTest {
    record Unit(int size, List<String> ranking) {
        Unit(int size, String... ranking) { this(size, List.of(ranking)); }
    }
    record Objective(int students, long score) implements Comparable<Objective> {
        public int compareTo(Objective other) {
            int count = Integer.compare(students, other.students);
            return count == 0 ? Long.compare(score, other.score) : count;
        }
    }

    @Test
    void G3_exactIndivisibleFeasibleSetAndPerStudentObjective() {
        verify(List.of(new Unit(2, "A"), new Unit(1, "A")), new Objective(3, 6));
        verify(List.of(new Unit(2, "A"), new Unit(1, "B")), new Objective(0, 0));
        verify(List.of(new Unit(5, "A"), new Unit(1, "A")), new Objective(5, 10));
        verify(List.of(new Unit(2, "A"), new Unit(3, "A")), new Objective(5, 10));
        verify(List.of(new Unit(4, "A"), new Unit(2, "A")), new Objective(4, 8));
        // A greedy five-person first-choice placement strands the only A-only pair and trio.
        verify(List.of(new Unit(5, "A", "B"), new Unit(3, "A"), new Unit(2, "A")), new Objective(10, 15));
        verify(List.of(new Unit(2, "A", "B"), new Unit(1, "B", "A")), new Objective(3, 5));
    }

    @Test
    void G3_splitUnitBranchingAboveTwelveProjectsMatchesSmallCoreOracleAndIndependentPadding() {
        List<Unit> core = List.of(
                new Unit(1, "A"), new Unit(1, "A"), new Unit(1, "A"), new Unit(1, "A"),
                new Unit(1, "B"), new Unit(1, "B"), new Unit(1, "B"),
                new Unit(2, "A", "B"));
        Objective coreOptimum = enumerate(core, 0, new int[2], 0, 0, 13);
        assertEquals(new Objective(9, 115), coreOptimum,
                "The independent exhaustive two-project core has a split-relaxation trap.");
        List<AllocationOptimizer.Project> projects = new ArrayList<>(List.of(
                new AllocationOptimizer.Project("A"), new AllocationOptimizer.Project("B")));
        List<AllocationOptimizer.Application> applications = applications(core);
        for (int projectIndex = 2; projectIndex < 13; projectIndex++) {
            String project = "P" + projectIndex;
            projects.add(new AllocationOptimizer.Project(project));
            for (int member = 0; member < 3; member++) {
                applications.add(new AllocationOptimizer.Application(project + "-" + member, List.of(project)));
            }
        }

        var result = new AllocationOptimizer().optimize(new AllocationOptimizer.Request(projects, applications));

        assertEquals(42, result.assignedStudents());
        assertEquals(544, result.preferenceScore());
        List<AllocationOptimizer.Assignment> unitAssignments = result.assignments().stream()
                .filter(row -> row.studentId().startsWith("s7-")).toList();
        assertEquals(2, unitAssignments.size());
        assertEquals(1, unitAssignments.stream().map(AllocationOptimizer.Assignment::projectId).distinct().count(),
                "The applying unit must remain indivisible.");
        assertEquals(13, result.assignments().stream().map(AllocationOptimizer.Assignment::projectId).distinct()
                .count());
    }

    @Test
    void G3_undersizedOptionalProjectBranchAboveTwelveMatchesIndependentCoreOracle() {
        List<Unit> core = List.of(
                new Unit(1, "A"), new Unit(1, "A"), new Unit(1, "A"),
                new Unit(1, "A", "B"), new Unit(1, "A", "B"), new Unit(1, "A", "B"),
                new Unit(1));
        Objective coreOptimum = enumerate(core, 0, new int[2], 0, 0, 13);
        assertEquals(new Objective(7, 76), coreOptimum,
                "The independent core requires the three flexible candidates to complete B.");
        List<AllocationOptimizer.Project> projects = new ArrayList<>(List.of(
                new AllocationOptimizer.Project("A"), new AllocationOptimizer.Project("B")));
        List<AllocationOptimizer.Application> applications = applications(core);
        Set<String> pinned = Set.of("s0-0");
        for (int projectIndex = 2; projectIndex < 13; projectIndex++) {
            String project = "P" + projectIndex;
            projects.add(new AllocationOptimizer.Project(project));
            for (int member = 0; member < 3; member++) {
                applications.add(new AllocationOptimizer.Application(project + "-" + member, List.of(project)));
            }
        }

        var result = new AllocationOptimizer().optimize(
                new AllocationOptimizer.Request(projects, applications, pinned));

        assertEquals(40, result.assignedStudents());
        assertEquals(505, result.preferenceScore());
        assertEquals(3, result.assignments().stream().filter(row -> row.projectId().equals("B")).count());
        assertTrue(result.assignments().stream().anyMatch(row -> row.studentId().equals("s0-0")
                && row.projectId().equals("A")));
        assertTrue(result.assignments().stream().anyMatch(row -> row.studentId().equals("s6-0")
                && row.preferenceRank() == 0));
        assertEquals(13, result.assignments().stream().map(AllocationOptimizer.Assignment::projectId).distinct()
                .count());
    }

    @Test
    void G3_largeSearchPreservesAssignedCountAndRankedSatisfactionPriorities() {
        List<Unit> countPriorityCore = List.of(
                new Unit(1, "A", "B"), new Unit(1, "A", "B"), new Unit(1, "A", "B"),
                new Unit(1, "A", "B"), new Unit(1, "A", "B"), new Unit(1, "A", "B"),
                new Unit(1, "B", "A"), new Unit(1, "B", "A"), new Unit(1, "B", "A"));
        Objective countCoreOptimum = enumerate(countPriorityCore, 0, new int[2], 0, 0, 13);
        assertEquals(new Objective(9, 116), countCoreOptimum);
        var countResult = optimizeWithIndependentPadding(countPriorityCore);
        assertEquals(42, countResult.assignedStudents());
        assertEquals(545, countResult.preferenceScore());

        List<Unit> satisfactionCore = List.of(
                new Unit(1, "A", "B"), new Unit(1, "A", "B"), new Unit(1, "A", "B"),
                new Unit(1, "B", "A"), new Unit(1, "B", "A"), new Unit(1, "B", "A"));
        Objective satisfactionOptimum = enumerate(satisfactionCore, 0, new int[2], 0, 0, 13);
        assertEquals(new Objective(6, 78), satisfactionOptimum);
        var satisfactionResult = optimizeWithIndependentPadding(satisfactionCore);
        assertEquals(39, satisfactionResult.assignedStudents());
        assertEquals(507, satisfactionResult.preferenceScore());
    }

    private AllocationOptimizer.Result optimizeWithIndependentPadding(List<Unit> core) {
        List<AllocationOptimizer.Project> projects = new ArrayList<>(List.of(
                new AllocationOptimizer.Project("A"), new AllocationOptimizer.Project("B")));
        List<AllocationOptimizer.Application> applications = applications(core);
        for (int projectIndex = 2; projectIndex < 13; projectIndex++) {
            String project = "P" + projectIndex;
            projects.add(new AllocationOptimizer.Project(project));
            for (int member = 0; member < 3; member++) {
                applications.add(new AllocationOptimizer.Application(project + "-" + member, List.of(project)));
            }
        }
        return new AllocationOptimizer().optimize(new AllocationOptimizer.Request(projects, applications));
    }

    private List<AllocationOptimizer.Application> applications(List<Unit> units) {
        List<AllocationOptimizer.Application> applications = new ArrayList<>();
        for (int unit = 0; unit < units.size(); unit++) {
            Unit input = units.get(unit);
            for (int member = 0; member < input.size; member++) {
                applications.add(new AllocationOptimizer.Application("s" + unit + "-" + member, input.ranking,
                        input.size == 1 ? null : "unit-" + unit, !input.ranking.isEmpty()));
            }
        }
        return applications;
    }

    private void verify(List<Unit> units, Objective expected) {
        Objective enumerated = enumerate(units, 0, new int[2], 0, 0, 2);
        assertEquals(expected, enumerated, "Fixture's independently enumerated optimum");
        List<AllocationOptimizer.Application> applications = new ArrayList<>();
        for (int unit = 0; unit < units.size(); unit++) {
            Unit input = units.get(unit);
            for (int member = 0; member < input.size; member++) {
                applications.add(new AllocationOptimizer.Application("s" + unit + "-" + member, input.ranking,
                        input.size == 1 ? null : "unit-" + unit));
            }
        }
        var result = new AllocationOptimizer().optimize(new AllocationOptimizer.Request(
                List.of(new AllocationOptimizer.Project("A"), new AllocationOptimizer.Project("B")), applications));
        assertEquals(enumerated.students, result.assignedStudents());
        assertEquals(enumerated.score, result.preferenceScore());
        Set<String> assigned = new HashSet<>();
        result.assignments().forEach(row -> assertTrue(assigned.add(row.studentId()), "No duplicate assignment"));
        for (int unit = 0; unit < units.size(); unit++) {
            String prefix = "s" + unit + "-";
            var rows = result.assignments().stream().filter(row -> row.studentId().startsWith(prefix)).toList();
            assertTrue(rows.isEmpty() || rows.size() == units.get(unit).size, "Indivisible membership");
            assertTrue(rows.stream().map(AllocationOptimizer.Assignment::projectId).distinct().count() <= 1);
        }
    }

    private Objective enumerate(List<Unit> units, int index, int[] counts, int students, long score, int projectCount) {
        if (index == units.size()) {
            for (int count : counts) if (count != 0 && (count < 3 || count > 5)) return new Objective(-1, -1);
            return new Objective(students, score);
        }
        Unit unit = units.get(index);
        Objective best = enumerate(units, index + 1, counts, students, score, projectCount);
        int choices = unit.ranking.isEmpty() ? 2 : unit.ranking.size();
        for (int rank = 0; rank < choices; rank++) {
            int project = unit.ranking.isEmpty() ? rank : unit.ranking.get(rank).equals("A") ? 0 : 1;
            if (counts[project] + unit.size > 5) continue;
            counts[project] += unit.size;
            long earned = unit.ranking.isEmpty() ? 0 : (long) unit.size * (projectCount - rank);
            Objective candidate = enumerate(units, index + 1, counts, students + unit.size, score + earned, projectCount);
            if (candidate.compareTo(best) > 0) best = candidate;
            counts[project] -= unit.size;
        }
        return best;
    }
}
