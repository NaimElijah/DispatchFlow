package com.dispatchflow.planning.events;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * A published or completed plan has a new revision, with its cause, the routes it changed, the
 * vehicles it withdrew and the orders it added (DL §7.2, BR-77). Live Operations notifies the
 * affected drivers, Messaging the affected recipients, and Reporting recomputes the baseline when a
 * vehicle was withdrawn or an order added (FR-39, FR-267 to FR-271, ADR-109, ADR-112).
 *
 * <p>An event holds no record of its own (DL §7.3), so each withdrawn vehicle's cause is the entry
 * of {@code withdrawalCauses} at the vehicle's position in {@code withdrawnVehicles}.
 *
 * @param eventId the event's ID
 * @param occurredAt when the revision took effect
 * @param plan the plan
 * @param date its delivery date
 * @param revision the new revision number
 * @param cause why it was made
 * @param changedRoutes the routes whose driver, stops or withdrawal changed, or that joined or left
 *     the plan, in the plan's order
 * @param withdrawnVehicles the vehicles this revision withdrew mid-route (FR-267), in the plan's
 *     order
 * @param withdrawalCauses why each was withdrawn, at the same positions: the baseline leaves out an
 *     unavailable vehicle or one left without a driver, and gives one whose shift ran out its whole
 *     shift (FR-271)
 * @param addedOrders the orders an accepted re-optimization put into the plan that it held under no
 *     attempt before, in the plan's order; empty for every other cause (FR-39, ADR-112)
 */
public record PlanRevised(
        EventId eventId,
        Instant occurredAt,
        PlanId plan,
        DeliveryDate date,
        int revision,
        RevisionCause cause,
        List<RouteId> changedRoutes,
        List<VehicleId> withdrawnVehicles,
        List<WithdrawalCause> withdrawalCauses,
        List<OrderId> addedOrders)
        implements DomainEvent {

    /**
     * Checks every part is present and each withdrawn vehicle has its cause, and copies the lists.
     *
     * @throws AssertionError when the vehicles and their causes differ in number: only {@code Plan}
     *     raises this event, so that is a bug in it, never a refusal
     */
    public PlanRevised {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(cause, "cause");
        changedRoutes = List.copyOf(changedRoutes);
        withdrawnVehicles = List.copyOf(withdrawnVehicles);
        withdrawalCauses = List.copyOf(withdrawalCauses);
        addedOrders = List.copyOf(addedOrders);
        if (withdrawnVehicles.size() != withdrawalCauses.size()) {
            throw new AssertionError("each withdrawn vehicle has one cause");
        }
    }
}
