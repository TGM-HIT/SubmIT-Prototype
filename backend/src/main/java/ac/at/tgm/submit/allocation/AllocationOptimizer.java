package ac.at.tgm.submit.allocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Exact, deterministic optimizer: first maximizes assigned pinned students, then the total number of assigned students,
 * then the ranked preference score (rank r, zero-based, is worth projectCount - r points). Groups have 3 to 5 students,
 * every student gets at most one project, and projects may stay unassigned. Unpinned students only use ranked projects;
 * acknowledged administrator pins retain their existing eligibility override.
 *
 * <p>Complexity: every subset of viable projects (ranked by at least three students) is opened in turn and a min-cost flow
 * decides the all-solo assignment, giving O(2^P * S * SPFA) with S students and P viable projects. For U shared units,
 * each subset additionally enumerates each whole unit's eligible target or unassigned state, reserving its full size
 * before solving the residual solo flow. Worst-case branching is (P + 1)^U; memoization prunes dominated states with
 * identical unit index and reserved capacities. No greedy assignment or unit splitting is used. Subsets needing more
 * than S/3 projects are skipped. Requests above {@link AllocationLimits#MAX_STUDENTS} students or
 * {@link AllocationLimits#MAX_PROJECTS} projects are rejected with {@link AllocationLimitExceededException}.
 *
 * <p>Determinism: subsets are visited in ascending bitmask order over projects in request order, and a candidate replaces
 * the incumbent only when strictly better, so equal requests always yield equal results.
 */
public final class AllocationOptimizer {

    /** Accepted projects and student applications. Ids are non-blank; projects are unique; rankings only name known projects. */
    public record Request(List<Project> projects, List<Application> applications, Set<String> pinnedStudentIds) {

        public Request(List<Project> projects, List<Application> applications) {
            this(projects, applications, Set.of());
        }

        public Request {
            Objects.requireNonNull(projects, "projects must not be null");
            Objects.requireNonNull(applications, "applications must not be null");
            projects = List.copyOf(projects);
            applications = List.copyOf(applications);
            pinnedStudentIds = Set.copyOf(pinnedStudentIds);
            Set<String> projectIds = new HashSet<>();
            for (Project project : projects) {
                if (!projectIds.add(project.projectId())) {
                    throw new IllegalArgumentException("duplicate project id " + project.projectId());
                }
            }
            Set<String> studentIds = new HashSet<>();
            Set<String> knownStudents = new HashSet<>();
            for (Application application : applications) {
                if (!studentIds.add(application.studentId())) {
                    throw new IllegalArgumentException("duplicate student id " + application.studentId());
                }
                knownStudents.add(application.studentId());
                if (!projectIds.containsAll(application.rankedProjectIds())) {
                    throw new IllegalArgumentException("student " + application.studentId() + " ranks an unknown project");
                }
            }
            if (!knownStudents.containsAll(pinnedStudentIds)) {
                throw new IllegalArgumentException("pinned student must be in the request");
            }
        }
    }

    /** A project that may receive a group. */
    public record Project(String projectId) {

        public Project {
            requireId(projectId, "project id");
        }
    }

    /** A student's application: ranked project ids, most preferred first. */
    public record Application(String studentId, List<String> rankedProjectIds, String applyingUnitId,
            boolean hasPreferences) {

        public Application(String studentId, List<String> rankedProjectIds) {
            this(studentId, rankedProjectIds, null, true);
        }

        public Application(String studentId, List<String> rankedProjectIds, String applyingUnitId) {
            this(studentId, rankedProjectIds, applyingUnitId, true);
        }

        public Application {
            requireId(studentId, "student id");
            Objects.requireNonNull(rankedProjectIds, "rankedProjectIds must not be null");
            rankedProjectIds = List.copyOf(rankedProjectIds);
            if (rankedProjectIds.isEmpty() && hasPreferences) {
                throw new IllegalArgumentException("student " + studentId + " must rank at least one project");
            }
            rankedProjectIds.forEach(id -> requireId(id, "ranked project id"));
            if (new HashSet<>(rankedProjectIds).size() != rankedProjectIds.size()) {
                throw new IllegalArgumentException("student " + studentId + " ranks a project more than once");
            }
            if (applyingUnitId != null) {
                requireId(applyingUnitId, "applying unit id");
            }
        }
    }

    /**
     * Optimal allocation. Assignments are ordered by project (request order), then student (request order). Every project
     * that appears in assignments has three to five students. {@code preferenceScore} is the sum over assigned students of
     * (projectCount - zeroBasedRank).
     */
    public record Result(
            List<Assignment> assignments,
            List<UnassignedStudent> unassignedStudents,
            List<UnassignedProject> unassignedProjects,
            int assignedStudents,
            long preferenceScore) {

        public Result {
            assignments = List.copyOf(assignments);
            unassignedStudents = List.copyOf(unassignedStudents);
            unassignedProjects = List.copyOf(unassignedProjects);
        }
    }

    /** A student placed in a project; {@code preferenceRank} is 1-based (1 = first choice). */
    public record Assignment(String studentId, String projectId, int preferenceRank, boolean pinned) {
        public Assignment(String studentId, String projectId, int preferenceRank) {
            this(studentId, projectId, preferenceRank, false);
        }
    }

    /** An unassigned student with the reason and the student's ranked preferences. */
    public record UnassignedStudent(String studentId, StudentUnassignedReason reason, List<String> rankedProjectIds) {

        public UnassignedStudent {
            rankedProjectIds = List.copyOf(rankedProjectIds);
        }
    }

    /** A project without a group, with the reason and how many students ranked it. */
    public record UnassignedProject(String projectId, ProjectUnassignedReason reason, int interestedStudents) {
    }

    /** Why a student received no project. */
    public enum StudentUnassignedReason {
        /** None of the student's ranked projects forms a group in the optimal allocation. */
        RANKED_PROJECTS_NOT_FORMED,
        /** Some ranked projects form groups, and every one of them already has five students. */
        RANKED_GROUPS_FULL,
        /** A formed ranked project still has a free seat, but the global objective does not place this student. */
        NOT_SELECTED_BY_OPTIMIZATION
    }

    /** Why a project forms no group. */
    public enum ProjectUnassignedReason {
        /** Fewer than three students ranked the project. */
        INSUFFICIENT_INTEREST,
        /** Enough students ranked it, but the optimal allocation places them elsewhere. */
        NOT_SELECTED_BY_OPTIMIZATION
    }

    private static void requireId(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
    }

    /** Returns the optimal allocation, or throws if the request exceeds the supported size. */
    public Result optimize(Request request) {
        Objects.requireNonNull(request, "request must not be null");
        checkLimits(request);
        Problem problem = new Problem(request);
        int[] best = new int[problem.students];
        Arrays.fill(best, -1);
        long bestValue = 0;
        int[] viable = problem.viableProjects();
        for (long mask = 1; mask < (1L << viable.length); mask++) {
            int[] open = problem.select(viable, mask);
            if (open.length * AllocationLimits.MIN_GROUP_SIZE > problem.students) {
                continue;
            }
            int[] assignment = problem.solve(open);
            long value = assignment == null ? -1 : problem.value(assignment);
            if (value > bestValue) {
                bestValue = value;
                best = assignment;
            }
        }
        return problem.toResult(best);
    }

    private static void checkLimits(Request request) {
        if (request.applications().size() > AllocationLimits.MAX_STUDENTS) {
            throw new AllocationLimitExceededException(
                    "at most " + AllocationLimits.MAX_STUDENTS + " students are supported, got " + request.applications().size());
        }
        if (request.projects().size() > AllocationLimits.MAX_PROJECTS) {
            throw new AllocationLimitExceededException(
                    "at most " + AllocationLimits.MAX_PROJECTS + " projects are supported, got " + request.projects().size());
        }
    }

    /** Index-based view of a request plus the objective. */
    private static final class Problem {

        private final Request request;
        private final int students;
        private final int projects;
        private final int[][] profit;
        private final int[] interest;
        private final boolean[] pinned;
        private final long pinWeight;
        private final long countWeight;
        private final List<String> projectIds;
        private final java.util.Map<String, Long> unitStates = new java.util.HashMap<>();

        Problem(Request request) {
            this.request = request;
            this.students = request.applications().size();
            this.projects = request.projects().size();
            this.profit = new int[students][projects];
            this.interest = new int[projects];
            this.pinned = new boolean[students];
            this.countWeight = (long) students * projects + 1;
            this.pinWeight = (long) students * (countWeight + projects) + 1;
            this.projectIds = request.projects().stream().map(Project::projectId).toList();
            for (int s = 0; s < students; s++) {
                List<String> ranking = request.applications().get(s).rankedProjectIds();
                pinned[s] = request.pinnedStudentIds().contains(request.applications().get(s).studentId());
                if (request.applications().get(s).hasPreferences()) {
                    for (int r = 0; r < ranking.size(); r++) {
                        int p = projectIds.indexOf(ranking.get(r));
                        profit[s][p] = projects - r;
                        interest[p]++;
                    }
                }
            }

        }

        int[] viableProjects() {
            return java.util.stream.IntStream.range(0, projects)
                    .filter(p -> java.util.stream.IntStream.range(0, students)
                            .filter(s -> canAssign(s, p))
                            .count() >= AllocationLimits.MIN_GROUP_SIZE).toArray();
        }

        private boolean canAssign(int student, int project) {
            Application application = request.applications().get(student);
            if (profit[student][project] > 0) {
                return true;
            }
            if (application.hasPreferences()) {
                return false;
            }
            return !pinned[student] || application.rankedProjectIds().contains(projectIds.get(project));
        }

        int[] select(int[] viable, long mask) {
            return java.util.stream.IntStream.range(0, viable.length)
                    .filter(i -> (mask & (1L << i)) != 0).map(i -> viable[i]).toArray();
        }

        /** Returns the best assignment for the open projects, or null if some open project cannot reach three students. */
        int[] solve(int[] open) {
            List<int[]> units = sharedUnits();
            if (!units.isEmpty()) {
                unitStates.clear();
                int[] fixed = new int[students];
                Arrays.fill(fixed, -1);
                return solveUnits(open, units, 0, fixed, new int[projects]);
            }
            return solveRemaining(open, new int[projects], null);
        }

        private List<int[]> sharedUnits() {
            var units = new java.util.LinkedHashMap<String, List<Integer>>();
            for (int s = 0; s < students; s++) {
                String unit = request.applications().get(s).applyingUnitId();
                if (unit != null) {
                    units.computeIfAbsent(unit, ignored -> new ArrayList<>()).add(s);
                }
            }
            return units.values().stream().map(members -> members.stream().mapToInt(Integer::intValue).toArray()).toList();
        }

        private int[] solveUnits(int[] open, List<int[]> units, int index, int[] fixed, int[] reserved) {
            if (dominated(index, fixed, reserved)) {
                return null;
            }
            return searchUnits(open, units, index, fixed, reserved);
        }

        private int[] searchUnits(int[] open, List<int[]> units, int index, int[] fixed, int[] reserved) {
            if (index == units.size()) {
                return solveRemaining(open, reserved, fixed);
            }
            int[] members = units.get(index);
            int[] best = solveUnits(open, units, index + 1, fixed, reserved);
            long bestValue = best == null ? -1 : value(best);
            for (int p : open) {
                if (!fits(members, p, reserved)) {
                    continue;
                }
                Arrays.stream(members).forEach(s -> fixed[s] = p);
                reserved[p] += members.length;
                int[] candidate = solveUnits(open, units, index + 1, fixed, reserved);
                long candidateValue = candidate == null ? -1 : value(candidate);
                if (candidateValue > bestValue) {
                    bestValue = candidateValue;
                    best = candidate;
                }
                reserved[p] -= members.length;
                Arrays.stream(members).forEach(s -> fixed[s] = -1);
            }
            return best;
        }

        private boolean dominated(int index, int[] fixed, int[] reserved) {
            String key = index + "|" + Arrays.toString(reserved);
            long score = value(fixed);
            Long previous = unitStates.get(key);
            if (previous != null && previous >= score) {
                return true;
            }
            unitStates.put(key, score);
            return false;
        }

        private boolean fits(int[] members, int project, int[] reserved) {
            return reserved[project] + members.length <= AllocationLimits.MAX_GROUP_SIZE
                    && Arrays.stream(members).allMatch(s -> profit[s][project] > 0);
        }

        private int[] solveRemaining(int[] open, int[] reserved, int[] fixed) {
            int[][] weighted = weighted(open);
            int bonus = (int) (students * (countWeight + pinWeight + projects) + 1);
            int[] assignment = new AllocationFlow(weighted, open, bonus, reserved).solve();
            mergeFixed(assignment, fixed);
            int[] sizes = sizes(assignment);
            return Arrays.stream(open).anyMatch(p -> sizes[p] < AllocationLimits.MIN_GROUP_SIZE) ? null : assignment;
        }

        private int[][] weighted(int[] open) {
            int[][] weighted = new int[students][projects];
            for (int s = 0; s < students; s++) {
                if (request.applications().get(s).applyingUnitId() != null) {
                    continue;
                }
                for (int p : open) {
                    weighted[s][p] = weight(s, p);
                }
            }
            return weighted;
        }

        private int weight(int student, int project) {
            if (profit[student][project] > 0) {
                return (int) (countWeight + profit[student][project] + (pinned[student] ? pinWeight : 0));
            }
            if (unrankedCandidateMayUse(student, project)) {
                return (int) (countWeight + (pinned[student] ? pinWeight : 0));
            }
            return 0;
        }

        private boolean unrankedCandidateMayUse(int student, int project) {
            return !request.applications().get(student).hasPreferences()
                    && (!pinned[student] || request.applications().get(student).rankedProjectIds()
                            .contains(projectIds.get(project)));
        }

        private void mergeFixed(int[] assignment, int[] fixed) {
            if (fixed != null) {
                for (int s = 0; s < students; s++) {
                    if (request.applications().get(s).applyingUnitId() != null) {
                        assignment[s] = fixed[s];
                    }
                }
            }
        }

        long value(int[] assignment) {
            long total = 0;
            for (int s = 0; s < students; s++) {
                if (assignment[s] >= 0) {
                    total += countWeight + profit[s][assignment[s]] + (pinned[s] ? pinWeight : 0);
                }
            }
            return total;
        }

        int[] sizes(int[] assignment) {
            int[] sizes = new int[projects];
            for (int project : assignment) {
                if (project >= 0) {
                    sizes[project]++;
                }
            }
            return sizes;
        }

        Result toResult(int[] assignment) {
            int[] sizes = sizes(assignment);
            List<Assignment> assignments = new ArrayList<>();
            List<UnassignedProject> unassignedProjects = new ArrayList<>();
            for (int p = 0; p < projects; p++) {
                if (sizes[p] > 0) {
                    assignments.addAll(members(assignment, p));
                } else {
                    unassignedProjects.add(unassignedProject(p));
                }
            }
            List<UnassignedStudent> unassigned = new ArrayList<>();
            int assigned = 0;
            long score = 0;
            for (int s = 0; s < students; s++) {
                if (assignment[s] >= 0) {
                    assigned++;
                    score += profit[s][assignment[s]];
                } else {
                    unassigned.add(unassignedStudent(s, sizes));
                }
            }
            return new Result(assignments, unassigned, unassignedProjects, assigned, score);
        }

        private List<Assignment> members(int[] assignment, int project) {
            List<Assignment> members = new ArrayList<>();
            for (int s = 0; s < students; s++) {
                if (assignment[s] == project) {
                    members.add(new Assignment(request.applications().get(s).studentId(), projectIds.get(project),
                            profit[s][project] == 0 ? 0 : projects - profit[s][project] + 1));
                }
            }
            return members;
        }

        private UnassignedProject unassignedProject(int project) {
            ProjectUnassignedReason reason = interest[project] < AllocationLimits.MIN_GROUP_SIZE
                    ? ProjectUnassignedReason.INSUFFICIENT_INTEREST
                    : ProjectUnassignedReason.NOT_SELECTED_BY_OPTIMIZATION;
            return new UnassignedProject(projectIds.get(project), reason, interest[project]);
        }

        private UnassignedStudent unassignedStudent(int student, int[] sizes) {
            Application source = request.applications().get(student);
            List<Integer> formedSizes = source.rankedProjectIds().stream()
                    .map(id -> sizes[projectIds.indexOf(id)]).filter(size -> size > 0).toList();
            StudentUnassignedReason reason = studentReason(formedSizes);
            return new UnassignedStudent(source.studentId(), reason, source.rankedProjectIds());
        }
    }

    /** Classifies an unassigned student from the sizes of the formed groups among their ranked projects. */
    static StudentUnassignedReason studentReason(List<Integer> formedRankedGroupSizes) {
        if (formedRankedGroupSizes.isEmpty()) {
            return StudentUnassignedReason.RANKED_PROJECTS_NOT_FORMED;
        }
        boolean allFull = formedRankedGroupSizes.stream().allMatch(size -> size >= AllocationLimits.MAX_GROUP_SIZE);
        return allFull ? StudentUnassignedReason.RANKED_GROUPS_FULL : StudentUnassignedReason.NOT_SELECTED_BY_OPTIMIZATION;
    }
}
