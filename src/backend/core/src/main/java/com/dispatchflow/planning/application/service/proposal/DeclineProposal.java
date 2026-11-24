package com.dispatchflow.planning.application.service.proposal;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Declines a re-optimization's proposal; the plan stays published and every route as it was (FR-255,
 * BR-26, DL §6.2).
 *
 * <p>{@link DeclineProposalChange} holds the transaction, which declines the proposal and writes the
 * audit record; after it commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>To Be Implemented.
 */
public final class DeclineProposal {

    private final LiveUpdates liveUpdates;
    private final DeclineProposalChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the dispatchers' open screens
     * @param change the decline's transaction
     */
    public DeclineProposal(LiveUpdates liveUpdates, DeclineProposalChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Declines the proposal.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher, recorded as who declined it
     * @param proposal the proposal
     * @param expected the proposal's version the dispatcher saw, from the preview's {@code version}
     *     (BR-76)
     * @throws NotFound when no proposal has the ID
     * @throws StaleVersion when the proposal changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code ProposalAlreadyDeclined}
     */
    public void decline(Actor actor, ProposalId proposal, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
