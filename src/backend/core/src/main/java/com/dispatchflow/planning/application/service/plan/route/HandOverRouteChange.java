package com.dispatchflow.planning.application.service.plan.route;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.application.service.plan.LayoutToJudge;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read and the transaction of {@link HandOverRoute}. {@link #layoutAfter} reads, in a read-only
 * transaction, the layout {@code Plan.afterHandOver} gives, for the evaluation. {@link #apply} then
 * loads the plan again, checks the version that read returned, asks Fleet whether the new driver can
 * take the route's vehicle, hands the route over with {@code Plan.handOver}, saves the plan, which
 * raises {@code PlanRevised} and {@code RouteHandedOver}, and writes the audit record (DL §6.3, TS
 * §6.2, ADR-062, ADR-063, ADR-129).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class HandOverRouteChange {

    private final PlanRepository plans;
    private final FleetFacts fleetFacts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the read and the transaction.
     *
     * @param plans where plans are kept
     * @param fleetFacts Fleet's hand-over check, which also asks Access whether the new driver is
     *     active and holds the Driver role
     * @param clock the time, for {@code today} and {@code now}
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public HandOverRouteChange(PlanRepository plans, FleetFacts fleetFacts, Clock clock, AuditLog auditLog) {
        this.plans = plans;
        this.fleetFacts = fleetFacts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Reads the plan as the hand-over would leave it, for the evaluation before the transaction.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param route the route
     * @param toDriver the new driver's account
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @return the plan's date, the version read and the layout after the hand-over
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with what {@code Plan.afterHandOver} refuses
     *     with: Planning's {@code PlanNotPublished}, {@code RouteNotInPlan}, {@code RouteWithdrawn}
     *     or {@code DriverAlreadyHasRoute}
     */
    @Transactional(readOnly = true)
    public LayoutToJudge layoutAfter(Actor actor, PlanId plan, RouteId route, AccountId toDriver, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Hands the route over, with the new driver's standing read here and the evaluation read before
     * the transaction.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher, recorded as who handed it over
     * @param plan the plan
     * @param route the route
     * @param toDriver the new driver's account
     * @param breakConfirmed whether the dispatcher confirmed a hand-over that breaks the new
     *     driver's shift (FR-304)
     * @param expected the plan's version {@link #layoutAfter} read
     * @param evaluation the validator's judgement of {@code Plan.afterHandOver}, if it could be read
     * @throws NotFound when no plan has the ID, or no account has {@code toDriver}
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotPublished},
     *     {@code DateInPast}, {@code RouteNotInPlan}, {@code RouteWithdrawn}, {@code
     *     DriverAlreadyHasRoute} (FR-302), {@code StaleEvaluation}, {@code NotAnActiveDriver},
     *     {@code DriverNotAvailable}, or {@code BreakNotConfirmed} (FR-304)
     */
    public void apply(
            Actor actor,
            PlanId plan,
            RouteId route,
            AccountId toDriver,
            boolean breakConfirmed,
            Version expected,
            Optional<Evaluation> evaluation) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
