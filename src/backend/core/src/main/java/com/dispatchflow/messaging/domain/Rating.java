package com.dispatchflow.messaging.domain;

import com.dispatchflow.messaging.domain.MessagingError.RatingOutOfRange;
import com.dispatchflow.shared.RuleViolation;

/**
 * A recipient's rating of a delivery: a whole number from 1 to 10 (FR-356, FR-376).
 *
 * @param value the rating
 */
public record Rating(int value) {

    /** The lowest rating. */
    public static final int LOWEST = 1;

    /** The highest rating. */
    public static final int HIGHEST = 10;

    /**
     * Checks the range.
     *
     * @throws RuleViolation with {@link RatingOutOfRange} outside 1 to 10
     */
    public Rating {
        if (value < LOWEST || value > HIGHEST) {
            throw new RuleViolation(new RatingOutOfRange(value));
        }
    }

    /**
     * Makes a rating.
     *
     * @param value the number
     * @return the rating
     * @throws RuleViolation with {@link RatingOutOfRange} outside 1 to 10
     */
    public static Rating of(int value) {
        return new Rating(value);
    }
}
