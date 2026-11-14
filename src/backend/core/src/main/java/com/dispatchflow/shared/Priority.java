package com.dispatchflow.shared;

import com.dispatchflow.shared.SharedKernelError.PriorityOutOfRange;

/**
 * An order's priority: a whole number from 1, the highest, to 5, the lowest (BR-53). It sets what
 * dropping the order costs (BR-54).
 *
 * @param level the priority, 1 to 5
 */
public record Priority(int level) {

    /** The highest priority. */
    public static final int HIGHEST = 1;

    /** The lowest priority. */
    public static final int LOWEST = 5;

    /** The priority an order gets when none is given (BR-53). */
    public static final int DEFAULT = 3;

    /**
     * Checks the level.
     *
     * @throws RuleViolation with {@link PriorityOutOfRange} when the level is outside 1 to 5
     */
    public Priority {
        if (level < HIGHEST || level > LOWEST) {
            throw new RuleViolation(new PriorityOutOfRange(level));
        }
    }

    /**
     * Creates the priority.
     *
     * @param level the priority, 1 to 5
     * @return the priority
     * @throws RuleViolation with {@link PriorityOutOfRange} when the level is outside 1 to 5
     */
    public static Priority of(int level) {
        return new Priority(level);
    }

    /**
     * The priority of an order given none (BR-53).
     *
     * @return priority 3
     */
    public static Priority byDefault() {
        return new Priority(DEFAULT);
    }
}
