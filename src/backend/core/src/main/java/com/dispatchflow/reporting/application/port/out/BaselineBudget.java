package com.dispatchflow.reporting.application.port.out;

/**
 * What ends the baseline's construction: a number of solutions found, never a wall-clock limit, so
 * the same request replays to the same answer (FR-39, NFR-13, ADR-044). The adapter sends it as the
 * optimizer's solution limit.
 *
 * <p>Built in M5.
 *
 * @param solutions how many solutions the construction may find, at least {@value #MIN_SOLUTIONS}
 */
public record BaselineBudget(int solutions) {

    /** The fewest solutions a budget allows: a budget of none would end before any baseline. */
    public static final int MIN_SOLUTIONS = 1;

    /**
     * Checks the budget allows at least one solution.
     *
     * @throws IllegalArgumentException when {@code solutions} is below {@value #MIN_SOLUTIONS}
     */
    public BaselineBudget {
        if (solutions < MIN_SOLUTIONS) {
            throw new IllegalArgumentException("solutions is below " + MIN_SOLUTIONS);
        }
    }
}
