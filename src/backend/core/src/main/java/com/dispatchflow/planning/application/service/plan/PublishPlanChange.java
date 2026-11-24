package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.access.application.api.Accounts;
import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.fleet.application.api.FleetFacts;
import com.dispatchflow.orders.application.api.OrdersForDate;
import com.dispatchflow.planning.application.port.out.plan.PlanRepository;
import com.dispatchflow.planning.domain.solution.Evaluation;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read and the transaction of {@link PublishPlan}. {@link #currentLayout} reads, in a read-only
 * transaction, the plan as it is, for the optimizer's re-check. {@link #apply} then loads the plan
 * again, checks the version that read returned, reads each route's roster answer and the date's
 * uncancelled orders, publishes the plan with {@code Plan.publish}, which runs BR-72's four steps in
 * order, saves it, which raises {@code PlanPublished}, and writes the audit record (DL §6.3, TS §6.2,
 * ADR-063, ADR-129).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class PublishPlanChange {

    private final PlanRepository plans;
    private final FleetFacts fleetFacts;
    private final Accounts accounts;
    private final OrdersForDate ordersForDate;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the read and the transaction.
     *
     * @param plans where plans are kept
     * @param fleetFacts Fleet's roster, for each route's vehicle and driver (BR-20)
     * @param accounts Access's answer on whether each driver holds the Driver role (BR-20)
     * @param ordersForDate the date's uncancelled orders (BR-22, BR-71)
     * @param clock the time, for {@code today} and {@code now}
     * @param auditLog the audit trail, written in the change's transaction (TS §6.2)
     */
    public PublishPlanChange(
            PlanRepository plans,
            FleetFacts fleetFacts,
            Accounts accounts,
            OrdersForDate ordersForDate,
            Clock clock,
            AuditLog auditLog) {
        this.plans = plans;
        this.fleetFacts = fleetFacts;
        this.accounts = accounts;
        this.ordersForDate = ordersForDate;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Reads the plan as it is, for the optimizer's re-check before the transaction.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param plan the plan
     * @param expected the plan's version the dispatcher saw (BR-76)
     * @return the plan's date, the version read and its layout
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}
     */
    @Transactional(readOnly = true)
    public LayoutToJudge currentLayout(Actor actor, PlanId plan, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Publishes the plan, with the re-check made before the transaction.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher, recorded as who published it
     * @param plan the plan
     * @param expected the plan's version {@link #currentLayout} read
     * @param recheck the optimizer's re-check of that version, or the one {@code
     *     FeasibilityChecker.unavailableFor} gave when the optimizer or the road network couldn't be
     *     reached
     * @throws NotFound when no plan has the ID
     * @throws StaleVersion when the plan changed since {@code expected}
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code PlanNotReady}, {@code
     *     DateInPast} (BR-118), or {@code StaleEvaluation} for a re-check of another version or
     *     layout
     * @throws com.dispatchflow.shared.RuleViolations with Planning's {@code PublishingRefused} and
     *     the first refusing step's refusals: {@code RouteNotStaffed} (BR-20, BR-63); every {@code
     *     OrderNotAccounted}, then every {@code OrderChangedSinceSolve} (BR-22, BR-71, FR-317);
     *     {@code OpenViolation} (BR-17); {@code RecheckUnavailable} or {@code RecheckFailed}
     *     (BR-21, BR-67, BR-70)
     */
    public void apply(Actor actor, PlanId plan, Version expected, Evaluation recheck) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
