package com.flightlog.allocation;

import java.util.Arrays;

/**
 * Max-profit assignment for a fixed set of open projects using successive shortest paths (SPFA).
 *
 * <p>Each project sink edge is split into {@code MIN_GROUP_SIZE} units carrying a large bonus and the remaining units with
 * no bonus, so a feasible allocation always fills the mandatory units first. Infeasibility is detected by the caller by
 * checking the minimum group size.
 */
final class AllocationFlow {

    private static final int NONE = -1;

    private final int[] to;
    private final int[] capacity;
    private final int[] cost;
    private final int[] next;
    private final int[] head;
    private final int[] studentEdgeProject;
    private int edgeCount;

    private final int students;
    private final int[][] profit;
    private final int[] open;

    /**
     * @param profit profit[student][project], or 0 if the student did not rank the project
     * @param open global indexes of the open projects
     * @param mandatoryBonus profit bonus of each of the first three seats of a project
     */
    AllocationFlow(int[][] profit, int[] open, int mandatoryBonus) {
        this.students = profit.length;
        this.profit = profit;
        this.open = open;
        int nodes = students + open.length + 2;
        int maxEdges = 2 * (students + students * open.length + 2 * open.length);
        this.to = new int[maxEdges];
        this.capacity = new int[maxEdges];
        this.cost = new int[maxEdges];
        this.next = new int[maxEdges];
        this.head = new int[nodes];
        this.studentEdgeProject = new int[maxEdges];
        Arrays.fill(head, NONE);
        Arrays.fill(studentEdgeProject, NONE);
        build(mandatoryBonus);
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

    private void build(int mandatoryBonus) {
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
            addEdge(1 + students + k, sink, AllocationLimits.MIN_GROUP_SIZE, -mandatoryBonus, NONE);
            addEdge(1 + students + k, sink, AllocationLimits.MAX_GROUP_SIZE - AllocationLimits.MIN_GROUP_SIZE, 0, NONE);
        }
    }

    private void addEdge(int from, int target, int cap, int edgeCost, int project) {
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
