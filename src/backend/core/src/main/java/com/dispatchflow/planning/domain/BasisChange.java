package com.dispatchflow.planning.domain;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;

/**
 * One change since a proposal's basis that the proposal moves, drops or relies on, which refuses its
 * acceptance and is named to the dispatcher (BR-25, BR-82, ADR-056, ADR-101).
 */
public sealed interface BasisChange {

    /**
     * A stop the proposal moves or drops has an accepted outcome or an arrival since the basis.
     *
     * @param stop the stop
     */
    record ReportedStop(StopId stop) implements BasisChange {}

    /**
     * An order the proposal serves or drops was cancelled since the basis.
     *
     * @param order the order
     */
    record CancelledOrder(OrderId order) implements BasisChange {}

    /**
     * An order the proposal serves or drops changed since the basis.
     *
     * @param order the order
     */
    record ChangedOrder(OrderId order) implements BasisChange {}

    /**
     * A vehicle the proposal uses changed in the fleet since the basis.
     *
     * @param vehicle the vehicle
     */
    record ChangedVehicle(VehicleId vehicle) implements BasisChange {}

    /**
     * A driver the proposal uses changed in the roster since the basis.
     *
     * @param driver the driver's account
     */
    record ChangedDriver(AccountId driver) implements BasisChange {}

    /**
     * The plan has a revision the proposal was not computed from, such as a hand-over or a late
     * outcome; applying the proposal would undo it.
     *
     * @param revision the revision made since the basis
     */
    record RevisionMade(RevisionNo revision) implements BasisChange {}
}
