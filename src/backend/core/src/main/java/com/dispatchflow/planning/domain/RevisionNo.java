package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.StoredStateInvalid;

/**
 * A numbered state of a published plan; publishing makes revision 1 and every later change makes
 * the next (DL §17, BR-77, ADR-059). Not the technical {@code Version}.
 *
 * <p>Only {@link com.dispatchflow.planning.domain.plan.Plan} makes one, from {@link #FIRST} and
 * {@link #next()}, so a number below 1 can only be damaged stored state, as with the kernel's {@code
 * Version}.
 *
 * @param value the revision number, 1 or more
 */
public record RevisionNo(int value) {

    /** The revision publishing makes. */
    public static final RevisionNo FIRST = new RevisionNo(1);

    /**
     * Checks a stored number.
     *
     * @throws StoredStateInvalid when it is below 1
     */
    public RevisionNo {
        if (value < 1) {
            throw new StoredStateInvalid(RevisionNo.class, "a revision number is 1 or more");
        }
    }

    /**
     * Rebuilds a stored revision number.
     *
     * @param value the stored number
     * @return the revision number
     * @throws StoredStateInvalid when it is below 1
     */
    public static RevisionNo of(int value) {
        return new RevisionNo(value);
    }

    /**
     * The number the next revision takes.
     *
     * @return this number plus one
     */
    public RevisionNo next() {
        return new RevisionNo(value + 1);
    }
}
