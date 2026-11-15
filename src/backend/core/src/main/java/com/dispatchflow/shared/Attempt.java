package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.InvalidAttempt;

/**
 * Which try at delivering an order this is, numbered from 1 (FR-258, DL §17). A put-back starts the
 * next one, and each attempt gets its own stop. Customers &amp; Orders counts attempts; Planning and
 * Live Operations hold the same value, so it lives in the kernel (ADR-111). Events carry it as an
 * {@code int}.
 *
 * <p>No user types an attempt's number. It comes from stored state, which the persistence adapter
 * rebuilds through {@link StoredStateInvalid#whileRestoring}, or from an event's {@code int}.
 *
 * @param number the attempt's number, 1 or more
 */
public record Attempt(int number) implements Comparable<Attempt> {

    /** The attempt a new order starts with. */
    public static final Attempt FIRST = new Attempt(1);

    /**
     * Checks the number.
     *
     * @throws RuleViolation with {@link InvalidAttempt} when the number is below 1
     */
    public Attempt {
        if (number < 1) {
            throw new RuleViolation(new InvalidAttempt(number));
        }
    }

    /**
     * Creates the attempt.
     *
     * @param number the attempt's number
     * @return the attempt
     * @throws RuleViolation with {@link InvalidAttempt} when the number is below 1
     */
    public static Attempt of(int number) {
        return new Attempt(number);
    }

    /**
     * The attempt a put-back starts (FR-258).
     *
     * @return this attempt's number plus one
     * @throws ArithmeticException when this attempt is numbered {@link Integer#MAX_VALUE}, which no
     *     order reaches, rather than wrapping to a negative number
     */
    public Attempt next() {
        return new Attempt(Math.addExact(number, 1));
    }

    @Override
    public int compareTo(Attempt other) {
        return Integer.compare(number, other.number);
    }
}
