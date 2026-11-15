package com.dispatchflow.reporting.application.port.out;

/**
 * What ends the baseline's construction: a number of solutions found, never a wall-clock limit, so
 * the same request replays to the same answer (FR-39, NFR-13, ADR-044). The adapter sends it as the
 * optimizer's solution limit.
 *
 * <p>Built in M5.
 *
 * @param solutions how many solutions the construction may find
 */
public record BaselineBudget(int solutions) {}
