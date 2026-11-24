package com.dispatchflow.planning.application.service.plan.reaction;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.planning.application.port.out.LiveUpdates;
import com.dispatchflow.planning.events.SolveSucceeded;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Applies a succeeded daily solve to its plan: the job's validated routes, drops and excluded vehicles
 * replace the plan's, and the plan becomes ready (BR-15, BR-59, FR-18, FR-197, DL §6.2). Its
 * listener calls it on {@link SolveSucceeded}. A re-optimization's event changes no plan: its
 * proposal waits for a dispatcher (FR-255).
 *
 * <p>It is idempotent by nature, so a repeated delivery is safe: {@code Plan.applySolveResult}
 * ignores a job the plan no longer waits for, an abandoned plan included (ADR-101). It writes its
 * audit record with the listener's server step (ADR-110).
 *
 * <p>{@link ApplySolveResultChange} holds the transaction, which applies the result and writes the
 * audit record; after it commits, the dispatchers' open screens are told (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class ApplySolveResult {

    private final LiveUpdates liveUpdates;
    private final ApplySolveResultChange change;

    /**
     * Creates the use case.
     *
     * @param liveUpdates the dispatchers' open screens (FR-222)
     * @param change the result's transaction
     */
    public ApplySolveResult(LiveUpdates liveUpdates, ApplySolveResultChange change) {
        this.liveUpdates = liveUpdates;
        this.change = change;
    }

    /**
     * Applies the job's result.
     *
     * <p>To Be Implemented.
     *
     * @param event the job's success
     * @param call the listener's server step, which the audit record names (ADR-110)
     * @throws com.dispatchflow.shared.RuleViolation with Planning's {@code RouteLocked}, {@code
     *     OrderAlreadyInPlan} (BR-07), {@code VehicleAlreadyHasRoute} or {@code DriverAlreadyHasRoute}
     *     (BR-39); each means a check upstream was skipped, so the event registry retries and then
     *     alerts the team (DL §12)
     * @throws StaleVersion when the plan changed between its read and its save; the listener's
     *     adapter retries at once, up to three times (DL §11)
     */
    public void on(SolveSucceeded event, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
