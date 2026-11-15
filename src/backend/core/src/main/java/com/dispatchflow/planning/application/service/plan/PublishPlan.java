package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Accounts;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.service.SolveRequestAssembler;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Publishes a ready plan as revision 1, so each driver sees their own route (FR-26, FR-234, FR-317,
 * BR-17, BR-20 to BR-22, BR-63, BR-67 to BR-72). Before its transaction it reads the facts {@code
 * PublishingChecks} holds: each route's roster answer, from Fleet's roster and Access's Driver role
 * (BR-20); the date's uncancelled orders (BR-22, BR-71); and the optimizer's re-check of the plan's
 * current version (BR-21). {@code Plan.publish} then runs BR-72's four steps in order. It raises
 * {@code PlanPublished}.
 *
 * <p>Built in M5.
 */
public final class PublishPlan {

    private final PlanRepository plans;
    private final SolveRequestAssembler assembler;
    private final FeasibilityChecker checker;
    private final FleetFacts fleetFacts;
    private final Accounts accounts;
    private final OrdersForDate ordersForDate;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param plans where plans are kept
     * @param assembler builds the re-check's evaluation request
     * @param checker the optimizer's validator
     * @param fleetFacts Fleet's roster, for each route's vehicle and driver (BR-20)
     * @param accounts Access's answer on whether each driver holds the Driver role (BR-20)
     * @param ordersForDate the date's uncancelled orders (BR-22, BR-71)
     * @param clock the time, for {@code today} and {@code now}
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public PublishPlan(
            PlanRepository plans,
            SolveRequestAssembler assembler,
            FeasibilityChecker checker,
            FleetFacts fleetFacts,
            Accounts accounts,
            OrdersForDate ordersForDate,
            Clock clock,
            LiveUpdates liveUpdates,
            AuditLog auditLog) {
        this.plans = plans;
        this.assembler = assembler;
        this.checker = checker;
        this.fleetFacts = fleetFacts;
        this.accounts = accounts;
        this.ordersForDate = ordersForDate;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Publishes the plan.
     *
     * <p>Built in M5.
     *
     * @param actor the dispatcher, recorded as who published it
     * @param plan the plan
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}, {@code
     *     DateInPast} (BR-118), or {@code StaleEvaluation} for a re-check of another version or layout
     * @throws com.dispatchflow.shared.RuleViolations with Planning's {@code PublishingRefused} and the
     *     first refusing step's refusals: {@code RouteNotStaffed} (BR-20, BR-63); every {@code
     *     OrderNotAccounted}, then every {@code OrderChangedSinceSolve} (BR-22, BR-71, FR-317); {@code
     *     OpenViolation} (BR-17); {@code RecheckUnavailable} or {@code RecheckFailed} (BR-21, BR-67,
     *     BR-70)
     */
    public void publish(Actor actor, PlanId plan, Version expected) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
