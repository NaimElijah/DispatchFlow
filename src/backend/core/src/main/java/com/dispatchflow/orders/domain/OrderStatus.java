package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.Attempt;

/**
 * An order's status, derived and never stored (BR-33, DL §9.6). It has three owners: the order says
 * whether it is cancelled, Planning where the current attempt is placed, and Live Operations what
 * happened at its stop. Mirroring it through each owner's actions would drift (DL §16).
 *
 * <p>A cancellation is still shown beside an outcome, from {@link Order#isCancelled()}.
 */
public enum OrderStatus {
    /** On no plan in force, or a put-back's new attempt (FR-258, BR-131, FR-337). */
    UNPLANNED,
    /** Served by a route of a plan in force. */
    PLANNED,
    /** Dropped by a plan in force (FR-18). */
    DROPPED,
    /** The current attempt's stop was delivered. */
    DELIVERED,
    /** The current attempt's stop failed. */
    FAILED,
    /** Cancelled, with no outcome for its current attempt (FR-58). */
    CANCELLED;

    /**
     * Derives an order's status, taking the first that fits (BR-33, DL §9.6):
     *
     * <ol>
     *   <li>delivered or failed: the current attempt's effective outcome, a fact, which comes before
     *       a cancellation so a delivery is never hidden (ADR-065);
     *   <li>cancelled;
     *   <li>planned or dropped: where a plan in force holds the current attempt; an abandoned or a
     *       cancelled plan doesn't count (BR-131, FR-337, BR-96);
     *   <li>unplanned: anything else.
     * </ol>
     *
     * <p>A placement or a fact about an earlier attempt is ignored, so a put-back is unplanned until
     * a plan places its new attempt (FR-258). An arrival alone is not an outcome.
     *
     * @param order the order
     * @param placement where Planning holds it
     * @param progress what happened at its stop
     * @return the status
     */
    public static OrderStatus derive(Order order, Placement placement, StopState progress) {
        return derive(order.attempt(), order.isCancelled(), placement, progress);
    }

    /**
     * Derives an order's status from the values a read model holds, so a screen of orders needn't
     * rebuild each aggregate (DL §9.6). The rules are those of {@link #derive(Order, Placement,
     * StopState)}.
     *
     * @param current the order's current attempt
     * @param cancelled whether the order is cancelled
     * @param placement where Planning holds it
     * @param progress what happened at its stop
     * @return the status
     */
    public static OrderStatus derive(Attempt current, boolean cancelled, Placement placement, StopState progress) {
        if (progress instanceof StopState.Delivered delivered
                && delivered.attempt().equals(current)) {
            return DELIVERED;
        }
        if (progress instanceof StopState.Failed failed && failed.attempt().equals(current)) {
            return FAILED;
        }
        if (cancelled) {
            return CANCELLED;
        }
        return switch (placement) {
            case Placement.Planned planned ->
                counts(planned.attempt(), planned.standing(), current) ? PLANNED : UNPLANNED;
            case Placement.Dropped dropped ->
                counts(dropped.attempt(), dropped.standing(), current) ? DROPPED : UNPLANNED;
            case Placement.NotPlaced notPlaced -> UNPLANNED;
        };
    }

    private static boolean counts(Attempt placed, Placement.PlanStanding standing, Attempt current) {
        return placed.equals(current) && standing == Placement.PlanStanding.IN_FORCE;
    }
}
