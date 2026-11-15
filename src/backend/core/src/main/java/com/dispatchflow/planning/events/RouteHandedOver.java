package com.dispatchflow.planning.events;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.Objects;

/**
 * A route moved to another driver (DL §7.2, FR-55). Planning's own listener moves the date's vehicle
 * assignment with it through Fleet (FR-191).
 *
 * @param eventId the event's ID
 * @param occurredAt when the hand-over took effect
 * @param plan the plan
 * @param date its delivery date
 * @param route the route handed over
 * @param vehicle the route's vehicle, which goes with it
 * @param fromDriver the driver who had it
 * @param toDriver the driver who has it now
 */
public record RouteHandedOver(
        EventId eventId,
        Instant occurredAt,
        PlanId plan,
        DeliveryDate date,
        RouteId route,
        VehicleId vehicle,
        AccountId fromDriver,
        AccountId toDriver)
        implements DomainEvent {

    /** Checks every part is present. */
    public RouteHandedOver {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(fromDriver, "fromDriver");
        Objects.requireNonNull(toDriver, "toDriver");
    }
}
