package com.dispatchflow.planning.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * What a re-optimization was computed from: the plan's revision, and when its facts were gathered
 * (DL §17 "Basis", BR-25, BR-82).
 *
 * @param revision the revision it started from
 * @param factsAt when the facts were gathered
 */
public record ProposalBasis(RevisionNo revision, Instant factsAt) {

    /** Checks both parts are present. */
    public ProposalBasis {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(factsAt, "factsAt");
    }
}
