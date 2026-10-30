package at.ac.tgm.submit.allocation;

import java.util.Arrays;

/**
 * Max-profit assignment relaxation for the selected project set using successive shortest paths (SPFA).
 *
 * <p>Mandatory projects' first remaining seats carry a dominating bonus; optional projects have no minimum in the
 * relaxation. The exact optimizer branches on optional projects receiving one or two seats, then enforces the group-size
 * rule. Infeasible mandatory lower bounds are detected by the caller.
 */
final class AllocationFlow {

    private static final int NONE = -1;

    private final int[] to;
    private final int[] capacity;
    private final long[] cost;
    private final int[] next;
    private final int[] head;
    private final int[] studentEdgeProject;
    private int edgeCount;

    private final int students;
    private final long[][] profit;
    private final int[] open;

    /**
     * @param profit profit[student][project], or 0 if the student did not rank the project
     * @param open global indexes of the projects still eligible for assignment
     * @param mandatoryBonus profit bonus for each remaining seat needed to satisfy an open project's minimum size
     * @param reserved students already fixed to each project
     * @param mandatory projects whose minimum group size is enforced in this solve
     */
    AllocationFlow(long[][] profit, int[] open, long mandatoryBonus, int[] reserved, boolean[] mandatory) {
        this.students = profit.length;
        this.profit = profit;
        this.open = open;
        int nodes = students + open.length + 2;
        int maxEdges = 2 * (students + students * open.length + 2 * open.length);
        this.to = new int[maxEdges];
        this.capacity = new int[maxEdges];
        this.cost = new long[maxEdges];
        this.next = new int[maxEdges];
        this.head = new int[nodes];
        this.studentEdgeProject = new int[maxEdges];
        Arrays.fill(head, NONE);
        Arrays.fill(studentEdgeProject, NONE);
        build(mandatoryBonus, reserved, mandatory);
    }

    /** Returns the assigned global project index per student, or -1 if unassigned. */
    int[] solve() {
        while (augment()) {
            continue;
        }
        int[] assignment = new int[students];
        Arrays.fill(assignment, NONE);
        for (int e = 0; e < edgeCount; e += 2) {
            if (studentEdgeProject[e] != NONE && capacity[e] == 0) {
                assignment[to[e ^ 1] - 1] = studentEdgeProject[e];
            }
        }
        return assignment;
    }

    private void build(long mandatoryBonus, int[] reserved, boolean[] mandatory) {
        int sink = students + open.length + 1;
        for (int s = 0; s < students; s++) {
            addEdge(0, 1 + s, 1, 0, NONE);
            for (int k = 0; k < open.length; k++) {
                if (profit[s][open[k]] > 0) {
                    addEdge(1 + s, 1 + students + k, 1, -profit[s][open[k]], open[k]);
                }
            }
        }
        for (int k = 0; k < open.length; k++) {
            int occupied = reserved.length == 0 ? 0 : reserved[open[k]];
            int mandatorySeats = mandatory[open[k]]
                    ? Math.max(0, AllocationLimits.MIN_GROUP_SIZE - occupied) : 0;
            addEdge(1 + students + k, sink, mandatorySeats, -mandatoryBonus, NONE);
            addEdge(1 + students + k, sink, AllocationLimits.MAX_GROUP_SIZE - occupied - mandatorySeats, 0, NONE);
        }
    }

    private void addEdge(int from, int target, int cap, long edgeCost, int project) {
        to[edgeCount] = target;
        capacity[edgeCount] = cap;
        cost[edgeCount] = edgeCost;
        studentEdgeProject[edgeCount] = project;
        next[edgeCount] = head[from];
        head[from] = edgeCount++;
        to[edgeCount] = from;
        capacity[edgeCount] = 0;
        cost[edgeCount] = -edgeCost;
        next[edgeCount] = head[target];
        head[target] = edgeCount++;
    }

    private boolean augment() {
        int nodes = head.length;
        int sink = nodes - 1;
        long[] dist = new long[nodes];
        int[] via = new int[nodes];
        Arrays.fill(dist, Long.MAX_VALUE);
        Arrays.fill(via, NONE);
        relaxAll(dist, via);
        if (dist[sink] >= 0) {
            return false;
        }
        for (int node = sink; node != 0; node = to[via[node] ^ 1]) {
            capacity[via[node]]--;
            capacity[via[node] ^ 1]++;
        }
        return true;
    }

    private void relaxAll(long[] dist, int[] via) {
        boolean[] queued = new boolean[head.length];
        int[] queue = new int[head.length + 1];
        int front = 0;
        int back = 0;
        dist[0] = 0;
        queue[back++] = 0;
        while (front != back) {
            int node = queue[front];
            front = (front + 1) % queue.length;
            queued[node] = false;
            for (int e = head[node]; e != NONE; e = next[e]) {
                if (capacity[e] > 0 && dist[node] + cost[e] < dist[to[e]]) {
                    dist[to[e]] = dist[node] + cost[e];
                    via[to[e]] = e;
                    if (!queued[to[e]]) {
                        queued[to[e]] = true;
                        queue[back] = to[e];
                        back = (back + 1) % queue.length;
                    }
                }
            }
        }
    }
}
