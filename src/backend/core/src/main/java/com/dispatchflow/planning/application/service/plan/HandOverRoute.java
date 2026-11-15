package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Accounts;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.service.SolveRequestAssembler;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Hands a route of a published plan to another driver; its stops and times stay, no optimization
 * runs, and its reported stops keep their deliveries (FR-55, FR-191, FR-192, FR-302 to FR-304,
 * FR-394, FR-395). Before its transaction it reads the new driver's {@code DriverStanding} from
 * Access's Driver role and Fleet's roster (FR-302), and asks the validator about {@code
 * Plan.afterHandOver}, with the vehicle rostered to the new driver (ADR-112). An unreachable optimizer
 * doesn't stop it: the route shows <em>not yet checked</em> until the re-check job runs (FR-395,
 * ADR-058). It raises {@code PlanRevised}, then {@code RouteHandedOver}, so the roster follows
 * (FR-191).
 *
 * <p>Built in M9.
 */
public final class HandOverRoute {

    private final PlanRepository plans;
    private final SolveRequestAssembler assembler;
    private final FeasibilityChecker checker;
    private final Accounts accounts;
    private final FleetFacts fleetFacts;
    private final Clock clock;
    private final LiveUpdates liveUpdates;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param plans where plans are kept
     * @param assembler builds the evaluation request
     * @param checker the optimizer's validator
     * @param accounts Access's answer on whether the new driver is active and holds the Driver role
     * @param fleetFacts Fleet's roster, for whether the new driver is available that date
     * @param clock the time, for {@code today} and {@code now}
     * @param liveUpdates the dispatchers' open screens
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public HandOverRoute(
            PlanRepository plans,
            SolveRequestAssembler assembler,
            FeasibilityChecker checker,
            Accounts accounts,
            FleetFacts fleetFacts,
            Clock clock,
            LiveUpdates liveUpdates,
            AuditLog auditLog) {
        this.plans = plans;
        this.assembler = assembler;
        this.checker = checker;
        this.accounts = accounts;
        this.fleetFacts = fleetFacts;
        this.clock = clock;
        this.liveUpdates = liveUpdates;
        this.auditLog = auditLog;
    }

    /**
     * Hands the route over.
     *
     * <p>Built in M9.
     *
     * @param actor the dispatcher, recorded as who handed it over
     * @param plan the plan
     * @param route the route
     * @param toDriver the new driver's account
     * @param breakConfirmed whether the dispatcher confirmed a hand-over that breaks the new driver's
     *     shift (FR-304)
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @throws NotFound when no plan has the ID, or no account has {@code toDriver}
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished}, {@code
     *     DateInPast}, {@code RouteNotInPlan}, {@code RouteWithdrawn}, {@code DriverAlreadyHasRoute}
     *     (FR-302), {@code StaleEvaluation}, {@code NotAnActiveDriver}, {@code DriverNotAvailable}, or
     *     {@code BreakNotConfirmed} (FR-304)
     */
    public void handOver(
            Actor actor, PlanId plan, RouteId route, AccountId toDriver, boolean breakConfirmed, Version expected) {
        throw new UnsupportedOperationException("Built in M9");
    }
}
