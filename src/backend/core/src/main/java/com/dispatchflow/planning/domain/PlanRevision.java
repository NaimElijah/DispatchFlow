package com.dispatchflow.planning.domain;

import com.dispatchflow.planning.events.RevisionCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.ProposalId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A published snapshot of every route of a plan, which the repository appends as rows when the plan
 * is saved (DL §4.4, ADR-059). The plan never loads past revisions; late reports are judged against
 * the revision in force at their corrected time (BR-110).
 *
 * @param number the revision number
 * @param effectiveAt when it took effect
 * @param cause why it was made (BR-77)
 * @param proposal the proposal it applies, for an accepted re-optimization (FR-104)
 * @param by who made it, when a person did
 * @param routes every route of the plan, in the plan's order
 */
public record PlanRevision(
        RevisionNo number,
        Instant effectiveAt,
        RevisionCause cause,
        Optional<ProposalId> proposal,
        Optional<AccountId> by,
        List<RevisionRoute> routes) {

    /** Checks every part is present, possibly empty, and copies the routes. */
    public PlanRevision {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(effectiveAt, "effectiveAt");
        Objects.requireNonNull(cause, "cause");
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(by, "by");
        routes = List.copyOf(routes);
    }
}
