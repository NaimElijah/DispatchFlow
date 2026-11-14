package com.dispatchflow.planning.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * The solve a plan's routes came from, and when the plan applied it. A failed later solve keeps it,
 * so the plan returns to ready (BR-59), and publishing reads its window: an order changed between
 * the request and the apply changed while the plan was solving (FR-317, ADR-101).
 *
 * @param basis the solve
 * @param appliedAt when the plan applied its result
 */
public record AppliedSolve(SolveBasis basis, Instant appliedAt) {

    /** Checks both parts are present. */
    public AppliedSolve {
        Objects.requireNonNull(basis, "basis");
        Objects.requireNonNull(appliedAt, "appliedAt");
    }

    /**
     * Whether an order's change fell inside the solve: after its request, and no later than its
     * apply (BR-71, FR-317).
     *
     * @param order the date's order
     * @return {@code true} when it was placed after the request, or changed while the plan solved
     */
    boolean changedDuring(DateOrder order) {
        Instant requestedAt = basis.requestedAt();
        boolean placedLate = order.placedAt().isAfter(requestedAt);
        boolean changedWhileSolving = order.lastChangedAt().isAfter(requestedAt)
                && !order.lastChangedAt().isAfter(appliedAt);
        return placedLate || changedWhileSolving;
    }
}
