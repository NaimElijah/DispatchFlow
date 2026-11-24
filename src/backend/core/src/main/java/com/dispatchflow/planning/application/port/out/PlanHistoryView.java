package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.planning.domain.RevisionNo;
import com.dispatchflow.planning.events.RevisionCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.RouteId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A published plan's revisions: who made each, when, why, and which routes it changed (FR-104,
 * BR-77, ADR-059). An accepted re-optimization names its proposal. The stops each change added,
 * removed or reordered on a route come from {@code RouteQueries.changesSince} (FR-249).
 *
 * <p>To Be Implemented.
 *
 * @param plan the plan
 * @param revisions its revisions, the first one first; empty for a plan never published
 */
public record PlanHistoryView(PlanId plan, List<Revision> revisions) {

    /** Checks the plan is present, and copies the revisions. */
    public PlanHistoryView {
        Objects.requireNonNull(plan, "plan");
        revisions = List.copyOf(revisions);
    }

    /**
     * One revision.
     *
     * <p>To Be Implemented.
     *
     * @param number its number, 1 for the publication
     * @param effectiveAt when it took effect
     * @param cause why it was made (BR-77)
     * @param proposal the proposal it applies, for an accepted re-optimization (FR-104)
     * @param by who made it, when a person did
     * @param changedRoutes the routes it changed, in the plan's order
     */
    public record Revision(
            RevisionNo number,
            Instant effectiveAt,
            RevisionCause cause,
            Optional<ProposalId> proposal,
            Optional<AccountId> by,
            List<RouteId> changedRoutes) {

        /** Checks every part is present, possibly empty, and copies the routes. */
        public Revision {
            Objects.requireNonNull(number, "number");
            Objects.requireNonNull(effectiveAt, "effectiveAt");
            Objects.requireNonNull(cause, "cause");
            Objects.requireNonNull(proposal, "proposal");
            Objects.requireNonNull(by, "by");
            changedRoutes = List.copyOf(changedRoutes);
        }
    }
}
