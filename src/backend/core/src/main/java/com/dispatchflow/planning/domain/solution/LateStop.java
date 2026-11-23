package com.dispatchflow.planning.domain.solution;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Duration;
import java.util.Objects;

/**
 * A stop a re-optimization keeps past its window end, flagged in the preview without refusing
 * acceptance (BR-107, FR-103).
 *
 * <p>A proposal stores each one in a row of its own, through this canonical constructor: it only
 * checks that every part is present, which the table's {@code NOT NULL} columns keep, so it needs no
 * instantiator of its own (ADR-126).
 *
 * @param order the order
 * @param attempt its attempt
 * @param late how far past the window end its service starts
 */
@Embeddable
public record LateStop(
        // PostgreSQL reserves "order".
        @Column(name = "order_id") OrderId order, Attempt attempt, Duration late) {

    /** Checks every part is present. */
    public LateStop {
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(late, "late");
    }
}
