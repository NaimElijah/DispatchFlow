package com.dispatchflow.planning.application.service.proposal;

import com.dispatchflow.access.application.api.Accounts;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.proposal.ProposalRepository;
import com.dispatchflow.planning.application.spi.ExecutionFacts;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link AcceptProposal}: it loads the proposal and its plan, checks the plan's
 * version, reads what was recorded since the proposal's basis as {@code ChangesSinceBasis}, makes the
 * proposal's routes the plan's new revision with {@code Plan.accept}, saves the plan, which raises
 * {@code PlanRevised}, and writes the audit record (TS §6.2, ADR-063, ADR-129).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class AcceptProposalChange {

    private final ProposalRepository proposals;
    private final PlanRepository plans;
    private final ExecutionFacts executionFacts;
    private final OrdersForDate ordersForDate;
    private final FleetFacts fleetFacts;
    private final Accounts accounts;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param proposals where proposals are kept
     * @param plans where plans are kept
     * @param executionFacts Live Operations' stops reported since the basis, among the plan's own
     *     (BR-25, BR-132)
     * @param ordersForDate the orders cancelled or changed since the basis (BR-82)
     * @param fleetFacts the vehicles and drivers changed since the basis, and the roster (BR-64)
     * @param accounts Access's answer on whether each driver holds the Driver role (BR-20, BR-64)
     * @param ids new IDs for the proposal's new routes and stops: one per route and one per stop
     *     (ADR-112)
     * @param clock the time, for {@code today} and {@code now}
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public AcceptProposalChange(
            ProposalRepository proposals,
            PlanRepository plans,
            ExecutionFacts executionFacts,
            OrdersForDate ordersForDate,
            FleetFacts fleetFacts,
            Accounts accounts,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.proposals = proposals;
        this.plans = plans;
        this.executionFacts = executionFacts;
        this.ordersForDate = ordersForDate;
        this.fleetFacts = fleetFacts;
        this.accounts = accounts;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Accepts the proposal, with what was recorded since its basis.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher, recorded as who accepted it (FR-104)
     * @param proposal the proposal
     * @param planVersion the plan's version the dispatcher saw, from the preview's {@code
     *     planVersion} (BR-76)
     * @return the plan the proposal changed, for the dispatchers' open screens
     * @throws NotFound when no proposal has the ID, or its plan doesn't exist
     * @throws StaleVersion when the plan changed since {@code planVersion}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished},
     *     {@code DateInPast}, {@code ProposalForAnotherPlan}, {@code ProposalDeclined}, {@code
     *     ProposalOutdated} naming every change, a revision since its basis included (BR-25,
     *     BR-82), {@code VehicleUnavailable} naming every vehicle (BR-64, BR-65), {@code
     *     OrderAlreadyInPlan}, {@code VehicleAlreadyHasRoute}, {@code DriverAlreadyHasRoute}, or
     *     {@code RouteIndexesUsedUp}
     */
    public PlanId apply(Actor actor, ProposalId proposal, Version planVersion) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
