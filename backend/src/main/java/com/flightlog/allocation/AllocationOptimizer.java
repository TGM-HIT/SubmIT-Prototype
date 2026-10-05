package com.flightlog.allocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Exact, deterministic optimizer: first maximizes the number of assigned students, then the ranked preference score
 * (rank r, zero-based, is worth projectCount - r points). Groups have 3 to 5 students, every student gets at most one
 * project, and projects may stay unassigned. Students can only be assigned to projects they ranked.
 *
 * <p>Complexity: every subset of viable projects (ranked by at least three students) is opened in turn and a min-cost flow
 * decides the assignment, giving O(2^P * S * SPFA) with S students and P viable projects. Subsets needing more than S/3
 * projects are skipped. Requests above {@link AllocationLimits#MAX_STUDENTS} students or
 * {@link AllocationLimits#MAX_PROJECTS} projects are rejected with {@link AllocationLimitExceededException}.
 *
 * <p>Determinism: subsets are visited in ascending bitmask order over projects in request order, and a candidate replaces
 * the incumbent only when strictly better, so equal requests always yield equal results.
 */
public final class AllocationOptimizer {

    /** Accepted projects and student applications. Ids are non-blank; projects are unique; rankings only name known projects. */
    public record Request(List<Project> projects, List<Application> applications) {

        public Request {
            Objects.requireNonNull(projects, "projects must not be null");
            Objects.requireNonNull(applications, "applications must not be null");
            projects = List.copyOf(projects);
            applications = List.copyOf(applications);
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
        }
    }

    /** A project that may receive a group. */
    public record Project(String projectId) {

        public Project {
            requireId(projectId, "project id");
        }
    }

    /** A student's application: ranked project ids, most preferred first. */
    public record Application(String studentId, List<String> rankedProjectIds) {

        public Application {
            requireId(studentId, "student id");
            Objects.requireNonNull(rankedProjectIds, "rankedProjectIds must not be null");
            rankedProjectIds = List.copyOf(rankedProjectIds);
            if (rankedProjectIds.isEmpty()) {
                throw new IllegalArgumentException("student " + studentId + " must rank at least one project");
            }
            rankedProjectIds.forEach(id -> requireId(id, "ranked project id"));
            if (new HashSet<>(rankedProjectIds).size() != rankedProjectIds.size()) {
                throw new IllegalArgumentException("student " + studentId + " ranks a project more than once");
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
    public record Assignment(String studentId, String projectId, int preferenceRank) {
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
        private final long countWeight;
        private final List<String> projectIds;

        Problem(Request request) {
            this.request = request;
            this.students = request.applications().size();
            this.projects = request.projects().size();
            this.profit = new int[students][projects];
            this.interest = new int[projects];
            this.projectIds = request.projects().stream().map(Project::projectId).toList();
            for (int s = 0; s < students; s++) {
                List<String> ranking = request.applications().get(s).rankedProjectIds();
                for (int r = 0; r < ranking.size(); r++) {
                    int p = projectIds.indexOf(ranking.get(r));
                    profit[s][p] = projects - r;
                    interest[p]++;
                }
            }
            this.countWeight = (long) students * projects + 1;
        }

        int[] viableProjects() {
            return java.util.stream.IntStream.range(0, projects)
                    .filter(p -> interest[p] >= AllocationLimits.MIN_GROUP_SIZE).toArray();
        }

        int[] select(int[] viable, long mask) {
            return java.util.stream.IntStream.range(0, viable.length)
                    .filter(i -> (mask & (1L << i)) != 0).map(i -> viable[i]).toArray();
        }

        /** Returns the best assignment for the open projects, or null if some open project cannot reach three students. */
        int[] solve(int[] open) {
            int[][] weighted = new int[students][projects];
            for (int s = 0; s < students; s++) {
                for (int p : open) {
                    weighted[s][p] = profit[s][p] == 0 ? 0 : (int) countWeight + profit[s][p];
                }
            }
            int bonus = (int) (students * (countWeight + projects) + 1);
            int[] assignment = new AllocationFlow(weighted, open, bonus).solve();
            int[] sizes = sizes(assignment);
            for (int p : open) {
                if (sizes[p] < AllocationLimits.MIN_GROUP_SIZE) {
                    return null;
                }
            }
            return assignment;
        }

        long value(int[] assignment) {
            long total = 0;
            for (int s = 0; s < students; s++) {
                if (assignment[s] >= 0) {
                    total += countWeight + profit[s][assignment[s]];
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
                            projects - profit[s][project] + 1));
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
