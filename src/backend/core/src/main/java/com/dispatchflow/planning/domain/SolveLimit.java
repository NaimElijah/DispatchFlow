package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.domain.PlanningError.SolveLimitOutOfRange;
import com.dispatchflow.shared.RuleViolation;
import java.time.Duration;
import java.util.Objects;

/**
 * How long a solve's search may run, from the start of its search (BR-75). A daily limit is 5 to 60
 * whole seconds, 45 by default (NFR-06, FR-19); a re-optimization's is 20 seconds (BR-84).
 *
 * @param duration the limit
 */
public record SolveLimit(Duration duration) {

    private static final long SHORTEST_SECONDS = 5;
    private static final long LONGEST_SECONDS = 60;

    /** The daily limit until an Admin sets another (FR-19). */
    public static final SolveLimit DEFAULT_DAILY = new SolveLimit(Duration.ofSeconds(45));

    /** The limit every re-optimization runs with (BR-84). */
    public static final SolveLimit REOPTIMIZATION = new SolveLimit(Duration.ofSeconds(20));

    /**
     * Checks the limit.
     *
     * @throws RuleViolation with {@link SolveLimitOutOfRange} when it is not 5 to 60 whole seconds
     */
    public SolveLimit {
        Objects.requireNonNull(duration, "duration");
        long seconds = duration.toSeconds();
        if (duration.toNanosPart() != 0 || seconds < SHORTEST_SECONDS || seconds > LONGEST_SECONDS) {
            throw new RuleViolation(new SolveLimitOutOfRange(seconds));
        }
    }

    /**
     * Creates a daily limit.
     *
     * @param duration the limit
     * @return the limit
     * @throws RuleViolation with {@link SolveLimitOutOfRange} when it is not 5 to 60 whole seconds
     */
    public static SolveLimit daily(Duration duration) {
        return new SolveLimit(duration);
    }
}
