package com.dispatchflow.liveops.domain;

import com.dispatchflow.shared.StoredStateInvalid;

/**
 * Which try at delivering an order a stop is, from 1 (FR-258, DL §17). Live Operations keeps its
 * own copy of the concept, which Customers &amp; Orders owns (DL §2.3, rule 1). Only that context
 * counts attempts, so a number below 1 can only come from damaged state.
 *
 * @param number the attempt's number, 1 or more
 */
public record Attempt(int number) implements Comparable<Attempt> {

    /**
     * Checks the number.
     *
     * @throws StoredStateInvalid when it is below 1
     */
    public Attempt {
        if (number < 1) {
            throw new StoredStateInvalid(Attempt.class, "an attempt is numbered from 1");
        }
    }

    /**
     * Creates the attempt.
     *
     * @param number the attempt's number
     * @return the attempt
     * @throws StoredStateInvalid when it is below 1
     */
    public static Attempt of(int number) {
        return new Attempt(number);
    }

    @Override
    public int compareTo(Attempt other) {
        return Integer.compare(number, other.number);
    }
}
