package com.dispatchflow.planning.application.service.proposal;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.planning.application.port.out.proposal.ProposalRepository;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link DeclineProposal}: it loads the proposal, checks its version, declines it,
 * saves it and writes the audit record (TS §6.2, ADR-063, ADR-129).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class DeclineProposalChange {

    private final ProposalRepository proposals;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param proposals where proposals are kept
     * @param clock the time, for {@code now}
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public DeclineProposalChange(ProposalRepository proposals, Clock clock, AuditLog auditLog) {
        this.proposals = proposals;
        this.clock = clock;
        this.auditLog = auditLog;
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
     * @return the plan the proposal would have changed, for the dispatchers' open screens
     * @throws NotFound when no proposal has the ID
     * @throws StaleVersion when the proposal changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code ProposalAlreadyDeclined}
     */
    public PlanId apply(Actor actor, ProposalId proposal, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
