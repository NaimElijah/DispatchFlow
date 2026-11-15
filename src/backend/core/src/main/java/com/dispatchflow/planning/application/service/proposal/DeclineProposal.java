package com.dispatchflow.planning.application.service.proposal;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.proposal.ProposalRepository;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Declines a re-optimization's proposal; the plan stays published and every route as it was (FR-255,
 * BR-26, DL §6.2).
 *
 * <p>Built in M9.
 */
public final class DeclineProposal {

    private final ProposalRepository proposals;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param proposals where proposals are kept
     * @param clock the time, for {@code now}
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public DeclineProposal(ProposalRepository proposals, Clock clock, LiveUpdates liveUpdates, AuditLog auditLog) {
        this.proposals = proposals;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Declines the proposal.
     *
     * <p>Built in M9.
     *
     * @param actor the dispatcher, recorded as who declined it
     * @param proposal the proposal
     * @param expected the proposal's version the dispatcher saw (BR-76)
     * @throws NotFound when no proposal has the ID
     * @throws StaleVersion when the proposal changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code ProposalAlreadyDeclined}
     */
    public void decline(Actor actor, ProposalId proposal, Version expected) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
