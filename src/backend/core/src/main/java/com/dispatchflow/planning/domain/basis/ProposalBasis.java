package com.dispatchflow.planning.domain.basis;

import com.dispatchflow.planning.domain.RevisionNo;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

/**
 * What a re-optimization was computed from: the plan's revision, and when its facts were gathered
 * (DL §17 "Basis", BR-25, BR-82).
 *
 * <p>A proposal stores it in two of its own columns, through this canonical constructor: it only
 * checks that both parts are present, which the table's {@code NOT NULL} columns keep, and the
 * revision number checks itself in its converter, so it needs no instantiator of its own (ADR-126).
 *
 * @param revision the revision it started from
 * @param factsAt when the facts were gathered
 */
@Embeddable
public record ProposalBasis(RevisionNo revision, Instant factsAt) {

    /** Checks both parts are present. */
    public ProposalBasis {
        Objects.requireNonNull(revision, "revision");
        Objects.requireNonNull(factsAt, "factsAt");
    }
}
