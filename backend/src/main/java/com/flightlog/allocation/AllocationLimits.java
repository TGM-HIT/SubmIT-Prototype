package com.flightlog.allocation;

/**
 * Hard bounds of the exact optimizer.
 *
 * <p>The search enumerates every subset of viable projects (at least {@link #MIN_GROUP_SIZE} interested students) and solves one
 * min-cost flow per subset, i.e. roughly O(2^P * S * (S * P)) in the worst case with S students and P viable projects.
 * Requests beyond these bounds are rejected instead of being solved heuristically.
 */
public final class AllocationLimits {

    public static final int MIN_GROUP_SIZE = 3;
    public static final int MAX_GROUP_SIZE = 5;
    public static final int MAX_STUDENTS = 60;
    public static final int MAX_PROJECTS = 12;

    private AllocationLimits() {
    }
}
