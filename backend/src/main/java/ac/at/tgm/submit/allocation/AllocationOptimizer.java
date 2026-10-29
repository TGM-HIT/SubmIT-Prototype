package ac.at.tgm.submit.allocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Exact, deterministic optimizer: first maximizes assigned pinned students, then total assignments, then ranked
 * preference score. Groups contain three to five students; applying units are indivisible.
 *
 * <p>Small instances retain the established project-subset traversal. Larger instances use exact branch-and-bound:
 * min-cost flow relaxes minimum group sizes and unresolved applying-unit indivisibility, and a branch is introduced only
 * when that relaxation returns an undersized group or a split unit. The flow objective is an upper bound, so dominated
 * branches are discarded. The problem remains combinatorial and may require substantial time and memory for difficult
 * inputs; the optimizer does not impose count ceilings or return an approximate result.
 */
public final class AllocationOptimizer {

    /** Accepted projects and student applications. Ids are non-blank; projects are unique; rankings name known projects. */
    public record Request(List<Project> projects, List<Application> applications, Set<String> pinnedStudentIds) {

        public Request(List<Project> projects, List<Application> applications) {
            this(projects, applications, Set.of());
        }

        public Request {
            Objects.requireNonNull(projects, "projects must not be null");
            Objects.requireNonNull(applications, "applications must not be null");
            Objects.requireNonNull(pinnedStudentIds, "pinnedStudentIds must not be null");
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
            for (Application application : applications) {
                if (!studentIds.add(application.studentId())) {
                    throw new IllegalArgumentException("duplicate student id " + application.studentId());
                }
                if (!projectIds.containsAll(application.rankedProjectIds())) {
                    throw new IllegalArgumentException("student " + application.studentId() + " ranks an unknown project");
                }
            }
            if (!studentIds.containsAll(pinnedStudentIds)) {
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

    /** Optimal allocation. Assignments are ordered by project, then student, in request order. */
    public record Result(List<Assignment> assignments, List<UnassignedStudent> unassignedStudents,
            List<UnassignedProject> unassignedProjects, int assignedStudents, long preferenceScore) {
        public Result {
            assignments = List.copyOf(assignments);
            unassignedStudents = List.copyOf(unassignedStudents);
            unassignedProjects = List.copyOf(unassignedProjects);
        }
    }

    /** A student placed in a project; preferenceRank is 1-based, with zero used for no-preference students. */
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

    public enum StudentUnassignedReason {
        RANKED_PROJECTS_NOT_FORMED,
        RANKED_GROUPS_FULL,
        NOT_SELECTED_BY_OPTIMIZATION
    }

    public enum ProjectUnassignedReason {
        INSUFFICIENT_INTEREST,
        NOT_SELECTED_BY_OPTIMIZATION
    }

    private static void requireId(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " must not be blank");
        }
    }

    /** Returns the exact optimal allocation. Runtime and memory depend on the input's combinatorial size. */
    public Result optimize(Request request) {
        Objects.requireNonNull(request, "request must not be null");
        Problem problem = new Problem(request);
        return problem.viableProjects().length <= Problem.LEGACY_SUBSET_CROSSOVER
                ? problem.optimizeLegacy() : problem.optimizeByBranchAndBound();
    }

    private static final class Problem {
        private static final int LEGACY_SUBSET_CROSSOVER = 12;
        private static final int CLOSED_PROJECT = -1;
        private static final int OPTIONAL_PROJECT = 0;
        private static final int REQUIRED_PROJECT = 1;
        private static final int UNFIXED_STUDENT = -1;
        private static final int EXCLUDED_STUDENT = -2;

        private record SearchNode(int[] projectState, int[] fixedStudents, int[] reservedSeats) {
        }

        private final Request request;
        private final int students;
        private final int projects;
        private final long[][] profit;
        private final int[] interest;
        private final boolean[] pinned;
        private final long pinWeight;
        private final long countWeight;
        private final List<String> projectIds;
        private final java.util.Map<String, Integer> projectIndexes = new java.util.HashMap<>();
        private final List<int[]> applyingUnits;
        private long bestValue = -1;
        private int[] bestAssignment;

        Problem(Request request) {
            this.request = request;
            students = request.applications().size();
            projects = request.projects().size();
            profit = new long[students][projects];
            interest = new int[projects];
            pinned = new boolean[students];
            countWeight = Math.addExact(Math.multiplyExact((long) students, projects), 1);
            pinWeight = Math.addExact(Math.multiplyExact((long) students,
                    Math.addExact(countWeight, projects)), 1);
            projectIds = request.projects().stream().map(Project::projectId).toList();
            for (int p = 0; p < projects; p++) {
                projectIndexes.put(projectIds.get(p), p);
            }
            for (int s = 0; s < students; s++) {
                Application application = request.applications().get(s);
                pinned[s] = request.pinnedStudentIds().contains(application.studentId());
                if (application.hasPreferences()) {
                    List<String> ranking = application.rankedProjectIds();
                    for (int rank = 0; rank < ranking.size(); rank++) {
                        int project = projectIndexes.get(ranking.get(rank));
                        profit[s][project] = projects - rank;
                        interest[project]++;
                    }
                }
            }
            applyingUnits = sharedUnits();
            bestAssignment = new int[students];
            Arrays.fill(bestAssignment, -1);
        }

        Result optimizeLegacy() {
            long[] best = {0};
            int[] viable = viableProjects();
            enumerateLegacySubsets(viable, viable.length - 1, new boolean[viable.length], best);
            return toResult(bestAssignment);
        }

        private void enumerateLegacySubsets(int[] viable, int index, boolean[] included, long[] best) {
            if (index < 0) {
                int[] open = java.util.stream.IntStream.range(0, viable.length)
                        .filter(bit -> included[bit]).map(bit -> viable[bit]).toArray();
                if (open.length == 0 || open.length * AllocationLimits.MIN_GROUP_SIZE > students) {
                    return;
                }
                int[] candidate = solve(open);
                long candidateValue = candidate == null ? -1 : value(candidate);
                if (candidateValue > best[0]) {
                    best[0] = candidateValue;
                    bestAssignment = candidate;
                }
                return;
            }
            included[index] = false;
            enumerateLegacySubsets(viable, index - 1, included, best);
            included[index] = true;
            enumerateLegacySubsets(viable, index - 1, included, best);
        }

        Result optimizeByBranchAndBound() {
            int[] state = new int[projects];
            int[] fixed = new int[students];
            Arrays.fill(fixed, UNFIXED_STUDENT);
            search(new SearchNode(state, fixed, new int[projects]));
            return toResult(bestAssignment);
        }

        private void search(SearchNode node) {
            int[] assignment = relaxedAssignment(node);
            if (assignment == null) {
                return;
            }
            long upperBound = value(assignment);
            if (upperBound <= bestValue) {
                return;
            }
            int split = splitUnit(assignment, node.fixedStudents());
            if (split >= 0) {
                branchUnit(node, split, assignment);
                return;
            }
            int undersized = undersizedOptionalProject(node, assignment);
            if (undersized >= 0) {
                int[] opened = node.projectState().clone();
                opened[undersized] = REQUIRED_PROJECT;
                search(new SearchNode(opened, node.fixedStudents(), node.reservedSeats()));
                int[] closed = node.projectState().clone();
                closed[undersized] = CLOSED_PROJECT;
                search(new SearchNode(closed, node.fixedStudents(), node.reservedSeats()));
                return;
            }
            if (upperBound > bestValue) {
                bestValue = upperBound;
                bestAssignment = assignment;
            }
        }

        private int[] relaxedAssignment(SearchNode node) {
            int[] open = openProjects(node.projectState());
            boolean[] mandatory = mandatoryProjects(node.projectState(), node.reservedSeats());
            long[][] weighted = relaxationWeights(open, node.fixedStudents());
            int[] assignment = new AllocationFlow(weighted, open, mandatoryBonus(),
                    node.reservedSeats(), mandatory).solve();
            mergeFixedStudents(assignment, node.fixedStudents());
            return satisfiesRequiredProjects(node.projectState(), assignment) ? assignment : null;
        }

        private int[] openProjects(int[] state) {
            return java.util.stream.IntStream.range(0, projects).filter(p -> state[p] != CLOSED_PROJECT).toArray();
        }

        private boolean[] mandatoryProjects(int[] state, int[] reserved) {
            boolean[] mandatory = new boolean[projects];
            for (int p = 0; p < projects; p++) {
                mandatory[p] = state[p] == REQUIRED_PROJECT || reserved[p] > 0;
            }
            return mandatory;
        }

        private long[][] relaxationWeights(int[] open, int[] fixed) {
            long[][] weighted = new long[students][projects];
            for (int s = 0; s < students; s++) {
                if (fixed[s] != UNFIXED_STUDENT) {
                    continue;
                }
                for (int p : open) {
                    weighted[s][p] = weight(s, p);
                }
            }
            return weighted;
        }

        private long mandatoryBonus() {
            return Math.addExact(Math.multiplyExact((long) students,
                    Math.addExact(pinWeight, Math.addExact(countWeight, projects))), 1);
        }

        private void mergeFixedStudents(int[] assignment, int[] fixed) {
            for (int s = 0; s < students; s++) {
                if (fixed[s] >= 0) {
                    assignment[s] = fixed[s];
                } else if (fixed[s] == EXCLUDED_STUDENT) {
                    assignment[s] = -1;
                }
            }
        }

        private boolean satisfiesRequiredProjects(int[] state, int[] assignment) {
            int[] sizes = sizes(assignment);
            for (int p = 0; p < projects; p++) {
                if (state[p] == REQUIRED_PROJECT && sizes[p] < AllocationLimits.MIN_GROUP_SIZE) {
                    return false;
                }
            }
            return true;
        }

        private int splitUnit(int[] assignment, int[] fixed) {
            for (int i = 0; i < applyingUnits.size(); i++) {
                int[] members = applyingUnits.get(i);
                if (fixed[members[0]] != UNFIXED_STUDENT) {
                    continue;
                }
                int target = assignment[members[0]];
                for (int member : members) {
                    if (assignment[member] != target) {
                        return i;
                    }
                }
            }
            return -1;
        }

        private void branchUnit(SearchNode node, int unitIndex, int[] relaxed) {
            int[] members = applyingUnits.get(unitIndex);
            boolean[] tried = branchRelaxedTargets(node, members, relaxed);
            branchAlternativeTargets(node, members, tried);
            excludeUnit(node, members);
        }

        private boolean[] branchRelaxedTargets(SearchNode node, int[] members, int[] relaxed) {
            boolean[] tried = new boolean[projects];
            for (int member : members) {
                int project = relaxed[member];
                if (project >= 0 && !tried[project]) {
                    tried[project] = true;
                    placeUnit(node, members, project);
                }
            }
            return tried;
        }

        private void branchAlternativeTargets(SearchNode node, int[] members, boolean[] tried) {
            for (int project = 0; project < projects; project++) {
                if (node.projectState()[project] != CLOSED_PROJECT && !tried[project]) {
                    placeUnit(node, members, project);
                }
            }
        }

        private void excludeUnit(SearchNode node, int[] members) {
            int[] excluded = node.fixedStudents().clone();
            for (int member : members) {
                excluded[member] = EXCLUDED_STUDENT;
            }
            search(new SearchNode(node.projectState(), excluded, node.reservedSeats()));
        }

        private void placeUnit(SearchNode node, int[] members, int project) {
            if (node.projectState()[project] == CLOSED_PROJECT || !fits(members, project, node.reservedSeats())) {
                return;
            }
            int[] nextState = node.projectState().clone();
            nextState[project] = REQUIRED_PROJECT;
            int[] nextFixed = node.fixedStudents().clone();
            for (int member : members) {
                nextFixed[member] = project;
            }
            int[] nextReserved = node.reservedSeats().clone();
            nextReserved[project] += members.length;
            search(new SearchNode(nextState, nextFixed, nextReserved));
        }

        private int undersizedOptionalProject(SearchNode node, int[] assignment) {
            int[] sizes = sizes(assignment);
            for (int p = 0; p < projects; p++) {
                if (node.projectState()[p] == OPTIONAL_PROJECT && node.reservedSeats()[p] == 0 && sizes[p] > 0
                        && sizes[p] < AllocationLimits.MIN_GROUP_SIZE) {
                    return p;
                }
            }
            return -1;
        }

        private int[] viableProjects() {
            return java.util.stream.IntStream.range(0, projects)
                    .filter(p -> java.util.stream.IntStream.range(0, students)
                            .filter(s -> canAssign(s, p)).count() >= AllocationLimits.MIN_GROUP_SIZE)
                    .toArray();
        }

        private boolean canAssign(int student, int project) {
            if (profit[student][project] > 0) {
                return true;
            }
            Application application = request.applications().get(student);
            return !application.hasPreferences()
                    && (!pinned[student] || application.rankedProjectIds().contains(projectIds.get(project)));
        }

        private int[] solve(int[] open) {
            if (applyingUnits.isEmpty()) {
                return solveRemaining(open, new int[projects], null);
            }
            return new LegacyUnitSearch(open).solve();
        }

        private List<int[]> sharedUnits() {
            var units = new java.util.LinkedHashMap<String, List<Integer>>();
            for (int s = 0; s < students; s++) {
                String unit = request.applications().get(s).applyingUnitId();
                if (unit != null) {
                    units.computeIfAbsent(unit, ignored -> new ArrayList<>()).add(s);
                }
            }
            return units.values().stream()
                    .map(members -> members.stream().mapToInt(Integer::intValue).toArray()).toList();
        }

        private final class LegacyUnitSearch {
            private final int[] open;
            private final java.util.Map<String, Long> visitedStates = new java.util.HashMap<>();

            LegacyUnitSearch(int[] open) {
                this.open = open;
            }

            int[] solve() {
                int[] fixed = new int[students];
                Arrays.fill(fixed, UNFIXED_STUDENT);
                return search(0, fixed, new int[projects]);
            }

            private int[] search(int index, int[] fixed, int[] reserved) {
                if (dominated(index, fixed, reserved)) {
                    return null;
                }
                if (index == applyingUnits.size()) {
                    return solveRemaining(open, reserved, fixed);
                }
                return branchTargets(index, fixed, reserved);
            }

            private int[] branchTargets(int index, int[] fixed, int[] reserved) {
                int[] members = applyingUnits.get(index);
                int[] best = search(index + 1, fixed, reserved);
                for (int project : open) {
                    if (fits(members, project, reserved)) {
                        best = tryProject(index, project, fixed, reserved, best);
                    }
                }
                return best;
            }

            private int[] tryProject(int index, int project, int[] fixed, int[] reserved, int[] best) {
                int[] members = applyingUnits.get(index);
                long bestValue = best == null ? -1 : value(best);
                for (int member : members) {
                    fixed[member] = project;
                }
                reserved[project] += members.length;
                int[] candidate = search(index + 1, fixed, reserved);
                long candidateValue = candidate == null ? -1 : value(candidate);
                reserved[project] -= members.length;
                for (int member : members) {
                    fixed[member] = UNFIXED_STUDENT;
                }
                return candidateValue > bestValue ? candidate : best;
            }

            private boolean dominated(int index, int[] fixed, int[] reserved) {
                String key = index + "|" + Arrays.toString(reserved);
                long score = value(fixed);
                Long previous = visitedStates.get(key);
                if (previous != null && previous >= score) {
                    return true;
                }
                visitedStates.put(key, score);
                return false;
            }
        }

        private boolean fits(int[] members, int project, int[] reserved) {
            return reserved[project] + members.length <= AllocationLimits.MAX_GROUP_SIZE
                    && Arrays.stream(members).allMatch(student -> profit[student][project] > 0);
        }

        private int[] solveRemaining(int[] open, int[] reserved, int[] fixed) {
            long[][] weighted = weighted(open);
            long bonus = Math.addExact(Math.multiplyExact((long) students,
                    Math.addExact(pinWeight, Math.addExact(countWeight, projects))), 1);
            boolean[] mandatory = new boolean[projects];
            for (int project : open) {
                mandatory[project] = true;
            }
            int[] assignment = new AllocationFlow(weighted, open, bonus, reserved, mandatory).solve();
            if (fixed != null) {
                for (int student = 0; student < students; student++) {
                    if (request.applications().get(student).applyingUnitId() != null) {
                        assignment[student] = fixed[student];
                    }
                }
            }
            int[] sizes = sizes(assignment);
            for (int project : open) {
                if (sizes[project] < AllocationLimits.MIN_GROUP_SIZE) {
                    return null;
                }
            }
            return assignment;
        }

        private long[][] weighted(int[] open) {
            long[][] result = new long[students][projects];
            for (int student = 0; student < students; student++) {
                if (request.applications().get(student).applyingUnitId() != null) {
                    continue;
                }
                for (int project : open) {
                    result[student][project] = weight(student, project);
                }
            }
            return result;
        }

        private long weight(int student, int project) {
            if (profit[student][project] > 0) {
                return countWeight + profit[student][project] + (pinned[student] ? pinWeight : 0);
            }
            Application application = request.applications().get(student);
            if (!application.hasPreferences()
                    && (!pinned[student] || application.rankedProjectIds().contains(projectIds.get(project)))) {
                return countWeight + (pinned[student] ? pinWeight : 0);
            }
            return 0;
        }

        private long value(int[] assignment) {
            long total = 0;
            for (int student = 0; student < students; student++) {
                int project = assignment[student];
                if (project >= 0) {
                    total += countWeight + profit[student][project] + (pinned[student] ? pinWeight : 0);
                }
            }
            return total;
        }

        private int[] sizes(int[] assignment) {
            int[] result = new int[projects];
            for (int project : assignment) {
                if (project >= 0) {
                    result[project]++;
                }
            }
            return result;
        }

        private Result toResult(int[] assignment) {
            int[] sizes = sizes(assignment);
            return new Result(assignments(assignment, sizes), unassignedStudents(assignment, sizes),
                    unassignedProjects(sizes), assignedCount(assignment), preferenceScore(assignment));
        }

        private List<Assignment> assignments(int[] assignment, int[] sizes) {
            List<Assignment> assigned = new ArrayList<>();
            for (int project = 0; project < projects; project++) {
                if (sizes[project] > 0) {
                    for (int student = 0; student < students; student++) {
                        if (assignment[student] == project) {
                            long earned = profit[student][project];
                            assigned.add(new Assignment(request.applications().get(student).studentId(),
                                    projectIds.get(project), earned == 0 ? 0 : projects - (int) earned + 1));
                        }
                    }
                }
            }
            return assigned;
        }

        private List<UnassignedProject> unassignedProjects(int[] sizes) {
            List<UnassignedProject> unassignedProjects = new ArrayList<>();
            for (int project = 0; project < projects; project++) {
                if (sizes[project] == 0) {
                    ProjectUnassignedReason reason = interest[project] < AllocationLimits.MIN_GROUP_SIZE
                            ? ProjectUnassignedReason.INSUFFICIENT_INTEREST
                            : ProjectUnassignedReason.NOT_SELECTED_BY_OPTIMIZATION;
                    unassignedProjects.add(new UnassignedProject(projectIds.get(project), reason, interest[project]));
                }
            }
            return unassignedProjects;
        }

        private List<UnassignedStudent> unassignedStudents(int[] assignment, int[] sizes) {
            List<UnassignedStudent> unassigned = new ArrayList<>();
            for (int student = 0; student < students; student++) {
                if (assignment[student] < 0) {
                    Application source = request.applications().get(student);
                    List<Integer> formedSizes = source.rankedProjectIds().stream()
                            .map(projectIds::indexOf).map(index -> sizes[index]).filter(size -> size > 0).toList();
                    unassigned.add(new UnassignedStudent(source.studentId(), studentReason(formedSizes),
                            source.rankedProjectIds()));
                }
            }
            return unassigned;
        }

        private int assignedCount(int[] assignment) {
            int count = 0;
            for (int project : assignment) {
                if (project >= 0) {
                    count++;
                }
            }
            return count;
        }

        private long preferenceScore(int[] assignment) {
            long score = 0;
            for (int student = 0; student < students; student++) {
                if (assignment[student] >= 0) {
                    score += profit[student][assignment[student]];
                }
            }
            return score;
        }
    }

    /** Classifies an unassigned student from the sizes of formed groups among their ranked projects. */
    static StudentUnassignedReason studentReason(List<Integer> formedRankedGroupSizes) {
        if (formedRankedGroupSizes.isEmpty()) {
            return StudentUnassignedReason.RANKED_PROJECTS_NOT_FORMED;
        }
        boolean allFull = formedRankedGroupSizes.stream().allMatch(size -> size >= AllocationLimits.MAX_GROUP_SIZE);
        return allFull ? StudentUnassignedReason.RANKED_GROUPS_FULL : StudentUnassignedReason.NOT_SELECTED_BY_OPTIMIZATION;
    }
}
