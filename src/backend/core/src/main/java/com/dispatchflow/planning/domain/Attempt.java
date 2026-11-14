package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.domain.PlanningError.InvalidAttempt;
import com.dispatchflow.shared.RuleViolation;

/**
 * Which try at delivering an order a stop is: 1 for the first, and one more for each put-back
 * (FR-258). Planning keeps its own copy of the concept, which Customers &amp; Orders owns (DL §2.3,
 * rule 1); a plan holds each order and attempt at most once (BR-07).
 *
 * @param number the attempt number, 1 or more
 */
public record Attempt(int number) {

    /** The first attempt at an order. */
    public static final Attempt FIRST = new Attempt(1);

    /**
     * Checks the number.
     *
     * @throws RuleViolation with {@link InvalidAttempt} when it is below 1
     */
    public Attempt {
        if (number < 1) {
            throw new RuleViolation(new InvalidAttempt(number));
        }
    }

    /**
     * Creates the attempt.
     *
     * @param number the attempt number
     * @return the attempt
     * @throws RuleViolation with {@link InvalidAttempt} when it is below 1
     */
    public static Attempt of(int number) {
        return new Attempt(number);
    }

    /**
     * The attempt a put-back starts.
     *
     * @return this attempt plus one
     */
    public Attempt next() {
        return new Attempt(number + 1);
    }
}
