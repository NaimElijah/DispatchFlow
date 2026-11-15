package com.dispatchflow.planning.application.service.proposal;

import com.dispatchflow.access.application.api.Accounts;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.port.out.proposal.ProposalRepository;
import com.dispatchflow.planning.application.spi.ExecutionFacts;
import com.dispatchflow.shared.ProposalId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Accepts a re-optimization: its routes, drops and excluded vehicles replace the plan's as a new
 * revision that cites it, and the drivers on the road receive the change (FR-32, FR-36, FR-104,
 * FR-255, BR-25, BR-62, BR-64, BR-65, BR-82, BR-83, ADR-055, ADR-056). Before its transaction it reads
 * what was recorded since the proposal's basis, as {@code ChangesSinceBasis}: the reported stops from
 * Live Operations' {@code ExecutionFacts.changesSince}, asked with the plan's own stops, the cancelled
 * and changed orders, the changed vehicles and drivers, and each vehicle's BR-20 standing from Fleet's
 * roster and Access's Driver role. It changes the plan, not the proposal, and raises {@code
 * PlanRevised}.
 *
 * <p>Because the operation acts on the plan, its {@code If-Match} carries the plan's ETag, not the
 * proposal's: the preview gives it as {@code ProposalPreviewView.planVersion} (BR-76, ADR-057).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M9.
 */
public final class AcceptProposal {

    private final ProposalRepository proposals;
    private final PlanRepository plans;
    private final ExecutionFacts executionFacts;
    private final OrdersForDate ordersForDate;
    private final FleetFacts fleetFacts;
    private final Accounts accounts;
    private final IdGenerator ids;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
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
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public AcceptProposal(
            ProposalRepository proposals,
            PlanRepository plans,
            ExecutionFacts executionFacts,
            OrdersForDate ordersForDate,
            FleetFacts fleetFacts,
            Accounts accounts,
            IdGenerator ids,
            Clock clock,
            LiveUpdates liveUpdates,
            AuditLog auditLog) {
        this.proposals = proposals;
        this.plans = plans;
        this.executionFacts = executionFacts;
        this.ordersForDate = ordersForDate;
        this.fleetFacts = fleetFacts;
        this.accounts = accounts;
        this.ids = ids;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Accepts the proposal.
     *
     * <p>Built in M9.
     *
     * @param actor the dispatcher, recorded as who accepted it (FR-104)
     * @param proposal the proposal
     * @param planVersion the plan's version the dispatcher saw, the aggregate this changes, from the
     *     preview's {@code planVersion} and sent as the plan's ETag (BR-76)
     * @throws NotFound when no proposal has the ID, or its plan doesn't exist
     * @throws StaleVersion when the plan changed since {@code planVersion}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished}, {@code
     *     DateInPast}, {@code ProposalForAnotherPlan}, {@code ProposalDeclined}, {@code
     *     ProposalOutdated} naming every change, a revision since its basis included (BR-25, BR-82),
     *     {@code VehicleUnavailable} naming every vehicle (BR-64, BR-65), {@code OrderAlreadyInPlan},
     *     {@code VehicleAlreadyHasRoute}, {@code DriverAlreadyHasRoute}, or {@code RouteIndexesUsedUp}
     */
    public void accept(Actor actor, ProposalId proposal, Version planVersion) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
