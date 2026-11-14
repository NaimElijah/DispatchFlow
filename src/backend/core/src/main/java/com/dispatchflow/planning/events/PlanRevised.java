package com.dispatchflow.planning.events;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.List;

/**
 * A published or completed plan has a new revision, with its cause and the routes it changed (DL
 * §7.2, BR-77). Live Operations notifies the affected drivers, Messaging the affected recipients,
 * and Reporting recomputes the baseline when a vehicle was withdrawn (FR-267 to FR-271).
 *
 * @param eventId the event's ID
 * @param occurredAt when the revision took effect
 * @param plan the plan
 * @param date its delivery date
 * @param revision the new revision number
 * @param cause why it was made
 * @param changedRoutes the routes whose driver, stops or withdrawal changed, or that joined or left
 *     the plan, in the plan's order
 * @param withdrawnVehicles the vehicles this revision withdrew mid-route (FR-267)
 */
public record PlanRevised(
        EventId eventId,
        Instant occurredAt,
        PlanId plan,
        DeliveryDate date,
        int revision,
        RevisionCause cause,
        List<RouteId> changedRoutes,
        List<VehicleId> withdrawnVehicles)
        implements DomainEvent {

    /** Copies the lists. */
    public PlanRevised {
        changedRoutes = List.copyOf(changedRoutes);
        withdrawnVehicles = List.copyOf(withdrawnVehicles);
    }
}
