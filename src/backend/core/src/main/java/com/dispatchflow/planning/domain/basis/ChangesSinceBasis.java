package com.dispatchflow.planning.domain.basis;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.util.List;

/**
 * What was recorded since a proposal's basis, which accepting it judges (BR-25, BR-82, ADR-056).
 * The use case reads it before its transaction. Plan revisions since the basis are the plan's own,
 * so they are not here.
 *
 * @param reportedStops stops with an accepted outcome or an arrival since the basis; a report held
 *     for the dispatcher doesn't count (BR-132, ADR-101)
 * @param cancelledOrders orders cancelled since the basis
 * @param changedOrders orders changed since the basis
 * @param changedVehicles vehicles changed in the fleet since the basis
 * @param changedDrivers drivers whose roster entry changed since the basis
 * @param standing each vehicle's BR-20 standing now (BR-64)
 */
public record ChangesSinceBasis(
        List<StopId> reportedStops,
        List<OrderId> cancelledOrders,
        List<OrderId> changedOrders,
        List<VehicleId> changedVehicles,
        List<AccountId> changedDrivers,
        List<VehicleStanding> standing) {

    /** Copies the lists. */
    public ChangesSinceBasis {
        reportedStops = List.copyOf(reportedStops);
        cancelledOrders = List.copyOf(cancelledOrders);
        changedOrders = List.copyOf(changedOrders);
        changedVehicles = List.copyOf(changedVehicles);
        changedDrivers = List.copyOf(changedDrivers);
        standing = List.copyOf(standing);
    }
}
