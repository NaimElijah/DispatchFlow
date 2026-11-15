package com.dispatchflow.liveops.domain;

import com.dispatchflow.liveops.domain.LiveOpsError.InvalidRevision;
import com.dispatchflow.shared.RuleViolation;

/**
 * A plan's revision number, as Live Operations holds it: the revision that entitled a report
 * (BR-110), and the last one a driver confirmed (FR-97). Live Operations keeps its own copy of
 * Planning's concept (DL §2.3, rule 1). A phone sends one when its driver taps *Seen*, so it is
 * checked as input.
 *
 * @param value the revision number, 1 or more
 */
public record RevisionNo(int value) implements Comparable<RevisionNo> {

    /**
     * Checks the number.
     *
     * @throws RuleViolation with {@link InvalidRevision} when it is below 1
     */
    public RevisionNo {
        if (value < 1) {
            throw new RuleViolation(new InvalidRevision(value));
        }
    }

    /**
     * Creates the revision number.
     *
     * @param value the number
     * @return the revision number
     * @throws RuleViolation with {@link InvalidRevision} when it is below 1
     */
    public static RevisionNo of(int value) {
        return new RevisionNo(value);
    }

    /**
     * Whether this revision comes after another.
     *
     * @param other the revision to compare with
     * @return {@code true} when this one is later
     */
    public boolean isAfter(RevisionNo other) {
        return value > other.value;
    }

    @Override
    public int compareTo(RevisionNo other) {
        return Integer.compare(value, other.value);
    }
}
