package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.StoredStateInvalid;

/**
 * Which try at delivering an order this is, from 1 (FR-258, DL §17). A put-back starts the next
 * one, and each attempt gets its own stop. Only this context counts attempts, so a number below 1
 * can only come from a damaged stored row.
 *
 * @param number the attempt's number, 1 or more
 */
public record Attempt(int number) implements Comparable<Attempt> {

    /** The attempt a new order starts with. */
    public static final Attempt FIRST = new Attempt(1);

    /**
     * Checks the number.
     *
     * @throws StoredStateInvalid when the number is below 1
     */
    public Attempt {
        if (number < 1) {
            throw new StoredStateInvalid(Attempt.class, "an attempt is numbered from 1");
        }
    }

    /**
     * Rebuilds a stored attempt.
     *
     * @param number the stored number
     * @return the attempt
     * @throws StoredStateInvalid when the number is below 1
     */
    public static Attempt of(int number) {
        return new Attempt(number);
    }

    /**
     * The attempt a put-back starts (FR-258).
     *
     * @return this attempt's number plus one
     */
    public Attempt next() {
        return new Attempt(Math.addExact(number, 1));
    }

    @Override
    public int compareTo(Attempt other) {
        return Integer.compare(number, other.number);
    }
}
