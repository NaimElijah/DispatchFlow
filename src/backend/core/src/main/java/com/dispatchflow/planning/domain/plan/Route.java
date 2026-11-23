package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.revision.RevisionRoute;
import com.dispatchflow.planning.domain.revision.RevisionStop;
import com.dispatchflow.planning.domain.solution.RoutePlan;
import com.dispatchflow.planning.domain.solution.Withdrawal;
import com.dispatchflow.planning.events.WithdrawalCause;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.VehicleId;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * One vehicle's and driver's ordered stops, an entity inside {@link Plan} keyed by its {@link
 * RouteId} (DL §4.4, §17 "Route", ADR-126). Only its plan changes it, in place, after the plan has
 * checked every rule of the command, and the plan checks the state it left at the end.
 *
 * <p>It is mapped for JPA (ADR-126, ADR-127): its lock, check state and withdrawal's cause are
 * stored by name, its confirmed return as a date and seconds, its times as {@link
 * StoredRoutePlan}'s columns, all of them or none, and its stops as {@link PlannedStop} entities in
 * order. Its list holds which stops it serves and in what order, never their lifecycle: the plan's
 * own collection of stops creates and deletes them, so a stop that moves to another route is never
 * an orphan of the route it left.
 */
@Entity
@Table(schema = "planning", name = "route")
class Route {

    @Id
    private UUID id;

    @Column(name = "route_index")
    private RouteIndex index;

    @Column(name = "vehicle_id")
    private VehicleId vehicle;

    @Column(name = "driver_id")
    private AccountId driver;

    @Enumerated(EnumType.STRING)
    @Column(name = "route_lock")
    private @Nullable RouteLock lock;

    // PostgreSQL reserves "check".
    @Enumerated(EnumType.STRING)
    @Column(name = "check_state")
    private CheckState check;

    @Enumerated(EnumType.STRING)
    private @Nullable WithdrawalCause withdrawalCause;

    private @Nullable DeliveryDate confirmedReturnDate;

    private @Nullable Long confirmedReturnSeconds;

    @Embedded
    private @Nullable StoredRoutePlan plan;

    // No removal and no orphan removal. Hibernate deletes an orphan of each list as it flushes the
    // list, and the order of the lists decides whether a moved stop stays: with orphan removal
    // here, a stop moved from a later route to an earlier one was deleted when the flush came at
    // commit. The plan's own collection of stops deletes them instead. Persist and merge still
    // cascade, because the merge in Spring Data's save would otherwise load a new stop on this
    // list by its ID, before it is inserted.
    @OneToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinColumn(name = "route_id", nullable = false)
    @OrderColumn(name = "position", nullable = false)
    private List<PlannedStop> stops = new ArrayList<>();

    /** For Hibernate, which sets the fields from the stored rows. */
    protected Route() {}

    /**
     * Creates a route that is checked and holds no lock, withdrawal, confirmed return, times or stops.
     *
     * @param id its ID
     * @param index its label and colour slot (FR-148)
     * @param vehicle its vehicle
     * @param driver its driver
     */
    Route(RouteId id, RouteIndex index, VehicleId vehicle, AccountId driver) {
        this.id = id.value();
        this.index = index;
        this.vehicle = vehicle;
        this.driver = driver;
        this.check = CheckState.CHECKED;
    }

    /** Rebuilds a route from a snapshot; it goes with {@code Plan.restore} (ADR-126). */
    static Route from(PlanSnapshot.RouteSnapshot snapshot) {
        Route route = new Route(snapshot.id(), snapshot.index(), snapshot.vehicle(), snapshot.driver());
        route.lockAs(snapshot.lock());
        route.checkAs(snapshot.check());
        route.withdrawAs(snapshot.withdrawal());
        route.confirmReturnAt(snapshot.confirmedReturn());
        route.timeAs(snapshot.plan());
        snapshot.stops().stream().map(PlannedStop::from).forEach(route.stops::add);
        return route;
    }

    RouteId id() {
        return RouteId.of(id);
    }

    RouteIndex index() {
        return index;
    }

    VehicleId vehicle() {
        return vehicle;
    }

    AccountId driver() {
        return driver;
    }

    Optional<RouteLock> lock() {
        return Optional.ofNullable(lock);
    }

    CheckState check() {
        return check;
    }

    Optional<Withdrawal> withdrawal() {
        return Optional.ofNullable(withdrawalCause).map(Withdrawal::new);
    }

    /**
     * The return time a confirmed hand-over set (FR-394).
     *
     * @return the time, or empty when no confirmed hand-over set one
     * @throws StoredStateInvalid when its stored seconds are negative
     */
    Optional<SolveTime> confirmedReturn() {
        if (confirmedReturnDate == null || confirmedReturnSeconds == null) {
            return Optional.empty();
        }
        DeliveryDate date = confirmedReturnDate;
        long seconds = confirmedReturnSeconds;
        return Optional.of(StoredStateInvalid.whileRestoring(SolveTime.class, () -> SolveTime.of(date, seconds)));
    }

    /**
     * Its departure, return and last leg.
     *
     * @return the times, or empty when the validator couldn't time it
     * @throws StoredStateInvalid when its stored times are refused by their own rules
     */
    Optional<RoutePlan> plan() {
        return Optional.ofNullable(plan).map(StoredRoutePlan::routePlan);
    }

    /**
     * Its stops, in order, as the live entities the plan moves between routes.
     *
     * @return an unmodifiable view of the stops
     */
    List<PlannedStop> stops() {
        return Collections.unmodifiableList(stops);
    }

    boolean withdrawn() {
        return withdrawalCause != null;
    }

    List<StopId> stopIds() {
        return stops.stream().map(PlannedStop::id).toList();
    }

    List<StopKey> keys() {
        return stops.stream().map(PlannedStop::key).toList();
    }

    void reindexAs(RouteIndex next) {
        this.index = next;
    }

    void driveBy(AccountId next) {
        this.driver = next;
    }

    void lockAs(Optional<RouteLock> next) {
        this.lock = next.orElse(null);
    }

    void checkAs(CheckState next) {
        this.check = next;
    }

    void withdrawAs(Optional<Withdrawal> next) {
        this.withdrawalCause = next.map(Withdrawal::cause).orElse(null);
    }

    void confirmReturnAt(Optional<SolveTime> next) {
        this.confirmedReturnDate = next.map(SolveTime::date).orElse(null);
        this.confirmedReturnSeconds = next.map(SolveTime::seconds).orElse(null);
    }

    void timeAs(Optional<RoutePlan> next) {
        this.plan = next.map(StoredRoutePlan::new).orElse(null);
    }

    void insert(int position, PlannedStop stop) {
        stops.add(position, stop);
    }

    void append(PlannedStop stop) {
        stops.add(stop);
    }

    /** Takes a stop off this route; the stop instance stays the same wherever it goes next. */
    void remove(PlannedStop stop) {
        if (!stops.remove(stop)) {
            throw new AssertionError("a stop is taken only off the route that holds it");
        }
    }

    /** Replaces the stops in the list Hibernate loaded, which a command never swaps for a new one. */
    void holdStops(List<PlannedStop> next) {
        stops.clear();
        stops.addAll(next);
    }

    /** The route as its plan's snapshot holds it; it goes with {@code Plan.snapshot} (ADR-126). */
    PlanSnapshot.RouteSnapshot snapshot() {
        return new PlanSnapshot.RouteSnapshot(
                id(),
                index,
                vehicle,
                driver,
                lock(),
                check,
                withdrawal(),
                confirmedReturn(),
                plan(),
                stops.stream().map(PlannedStop::snapshot).toList());
    }

    /** The route as a revision records it, with each stop's planned arrival (ADR-059). */
    RevisionRoute revision() {
        return new RevisionRoute(
                id(),
                vehicle,
                driver,
                stops.stream()
                        .map(stop -> new RevisionStop(
                                stop.id(),
                                stop.order(),
                                stop.attempt(),
                                stop.plan().map(stopPlan -> stopPlan.times().arrival())))
                        .toList());
    }
}
