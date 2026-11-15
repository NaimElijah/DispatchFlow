package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One route of a plan in force, as its current revision has it (DL §2.2, ADR-059, ADR-067,
 * ADR-101). Live Operations builds from it:
 *
 * <ul>
 *   <li>its {@code PlannedLegs}, from the planned departure and the stops' legs;
 *   <li>{@code TrackingStanding}'s current driver, and its shift end from the confirmed return when
 *       there is one (FR-328, FR-394);
 *   <li>{@code RouteRun.isConfirmationOverdue}'s revision and time (FR-97, FR-157);
 *   <li>{@code RouteRun.finish}'s pending stops, from the stops in order (FR-329);
 *   <li>its alerts' plan, and each driver's routes on a date (FR-27).
 * </ul>
 *
 * <p>Built in M5.
 *
 * @param plan the plan
 * @param date the plan's delivery date
 * @param route the route
 * @param vehicle its vehicle
 * @param driver its driver in the current revision
 * @param planRevision the number of the plan's current revision, 1 or more
 * @param changedIn the number of the revision that last changed this route, 1 or more
 * @param changedAt when that revision took effect
 * @param plannedDeparture when the route is planned to leave its start, or empty when the
 *     validator couldn't time it (ADR-112)
 * @param confirmedReturn the return time a confirmed hand-over set, if one did (FR-394)
 * @param stops its stops, in planned order
 */
public record PublishedRouteFact(
        PlanId plan,
        DeliveryDate date,
        RouteId route,
        VehicleId vehicle,
        AccountId driver,
        int planRevision,
        int changedIn,
        Instant changedAt,
        Optional<Instant> plannedDeparture,
        Optional<Instant> confirmedReturn,
        List<PublishedStopFact> stops) {

    /** Checks every part is present, possibly empty, and copies the stops. */
    public PublishedRouteFact {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(vehicle, "vehicle");
        Objects.requireNonNull(driver, "driver");
        Objects.requireNonNull(changedAt, "changedAt");
        Objects.requireNonNull(plannedDeparture, "plannedDeparture");
        Objects.requireNonNull(confirmedReturn, "confirmedReturn");
        stops = List.copyOf(stops);
    }
}
