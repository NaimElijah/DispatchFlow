package com.dispatchflow.planning.application.service.proposal;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Accepts a re-optimization: its routes, drops and excluded vehicles replace the plan's as a new
 * revision that cites it, and the drivers on the road receive the change (FR-32, FR-36, FR-104,
 * FR-255, BR-25, BR-62, BR-64, BR-65, BR-82, BR-83, ADR-055, ADR-056). In its transaction, {@link
 * AcceptProposalChange#apply} reads what was recorded since the proposal's basis, as {@code
 * ChangesSinceBasis}: the reported stops from Live Operations' {@code ExecutionFacts.changesSince},
 * asked with the plan's own stops, the cancelled and changed orders, the changed vehicles and
 * drivers, and each vehicle's BR-20 standing from Fleet's roster and Access's Driver role. These are
 * in-process calls, not outside ones (DL §6.3). It changes the plan, not the proposal, and raises
 * {@code PlanRevised}.
 *
 * <p>The request's target is the proposal, but the call changes the plan, so the plan's version
 * travels in the request body, not in {@code If-Match}: the preview gives it as {@code
 * ProposalPreviewView.planVersion} (BR-76, ADR-057). Which status a stale plan version answers is
 * for the {@code web} adapter's ADR to decide.
 *
 * <p>{@link AcceptProposalChange} holds the transaction, which reads those changes, accepts the
 * proposal and writes the audit record; after it commits, the dispatchers' open screens are told
 * (ADR-129).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>To Be Implemented.
 */
public final class AcceptProposal {

    private final LiveUpdates liveUpdates;
    private final AcceptProposalChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change the acceptance's transaction
     */
    public AcceptProposal(LiveUpdates liveUpdates, AcceptProposalChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Accepts the proposal.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher, recorded as who accepted it (FR-104)
     * @param proposal the proposal
     * @param planVersion the plan's version the dispatcher saw, the aggregate this changes, from
     *     the preview's {@code planVersion} and sent in the request body (BR-76)
     * @throws NotFound when no proposal has the ID, or its plan doesn't exist
     * @throws StaleVersion when the plan changed since {@code planVersion}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished},
     *     {@code DateInPast}, {@code ProposalForAnotherPlan}, {@code ProposalDeclined}, {@code
     *     ProposalOutdated} naming every change, a revision since its basis included (BR-25,
     *     BR-82), {@code VehicleUnavailable} naming every vehicle (BR-64, BR-65), {@code
     *     OrderAlreadyInPlan}, {@code VehicleAlreadyHasRoute}, {@code DriverAlreadyHasRoute}, or
     *     {@code RouteIndexesUsedUp}
     */
    public void accept(Actor actor, ProposalId proposal, Version planVersion) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
