package com.dispatchflow.planning.application.port.out;

import com.dispatchflow.planning.domain.settings.Buffer;
import com.dispatchflow.planning.domain.settings.DepartureAllowance;
import com.dispatchflow.planning.domain.settings.FixedVehicleCost;
import com.dispatchflow.planning.domain.settings.TravelBuckets;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.VehicleId;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.time.Duration;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The facts of one delivery date that a daily solve, a re-optimization and an evaluation all start
 * from, in Planning's own values (DL §2.3 rule 1, TS §2.1 rule 4): the date's uncancelled orders,
 * the depots, the request's vehicles and their drivers, the travel model's settings and the matrix.
 * {@code SolveRequestAssembler} builds it from Customers &amp; Orders' {@code OrdersForDate},
 * Fleet's {@code FleetFacts}, the planning settings, the configured {@link SolveConfiguration} and
 * the stored matrix; the optimizer adapter turns it into the {@code optimization-api} request, and the
 * Python {@code ProblemFactory} applies every rule (DL §10.2, ADR-050).
 *
 * <p>To Be Implemented.
 *
 * @param date the delivery date
 * @param orders the date's uncancelled orders, each once, at their current attempt (BR-22, FR-258);
 *     for a re-optimization, also every order on the current revision's routes that the date's
 *     orders lack, such as one put back to another date, so every stop has its order (ADR-050)
 * @param depots every depot a vehicle starts or ends at, each once
 * @param vehicles for a daily solve, the vehicles that may drive on the date: active, not marked
 *     unavailable, and rostered to an available driver, because the optimizer requires a driver for
 *     every daily vehicle (FR-56, FR-194, ADR-105); for an evaluation, every vehicle the layout
 *     routes, whatever its state; for a re-optimization, every vehicle of the plan, whatever its
 *     state, which the request's routes give (ADR-050)
 * @param drivers for a daily solve and a re-optimization, the date's rostered drivers who are
 *     available, each once (FR-54, FR-82); for an evaluation, each layout route's driver, rostered
 *     to that route's vehicle, whatever their availability, because the optimizer requires a driver
 *     for every vehicle (ADR-105, ADR-112). An evaluation never refuses a problem for BR-20: the
 *     calling use case checks the roster itself, through the domain's {@code RouteStaffing} or
 *     {@code DriverStanding}
 * @param travel the travel model's settings, and the configured values every solve takes (FR-321,
 *     FR-322, FR-227, FR-21, FR-67, BR-120, FR-81, ADR-121)
 * @param matrix the base travel matrix over every distinct location of the orders and depots
 *     (ADR-052): the plan's stored matrix, or a new one over the new list of locations when the
 *     stored one lacks a location (ADR-121)
 */
public record ProblemFacts(
        DeliveryDate date,
        List<Order> orders,
        List<Depot> depots,
        List<Vehicle> vehicles,
        List<Driver> drivers,
        TravelModel travel,
        TravelMatrix matrix) {

    /** Checks every part is present, and copies the lists. */
    public ProblemFacts {
        Objects.requireNonNull(date, "date");
        orders = List.copyOf(orders);
        depots = List.copyOf(depots);
        vehicles = List.copyOf(vehicles);
        drivers = List.copyOf(drivers);
        Objects.requireNonNull(travel, "travel");
        Objects.requireNonNull(matrix, "matrix");
    }

    /**
     * One order to serve or drop, as Customers &amp; Orders answered (FR-15, BR-14, BR-49, BR-50,
     * BR-53, BR-124, FR-364, FR-320). Its location is a customer's place, so it prints redacted.
     *
     * <p><b>Gap until the solve contract carries the flag.</b> The Python {@code OrderSpec} has no
     * input for an order's outside-service-area flag (FR-320), so the optimizer adapter has nowhere
     * to send it. The solve contract and its provider add that input, and the factory then drops
     * such an order as <em>unreachable location</em> (FR-66, ADR-139 decision 6).
     *
     * <p>To Be Implemented.
     *
     * @param order the order
     * @param attempt its current attempt
     * @param location its customer's coordinates, or empty, so it drops as <em>missing
     *     location</em> (BR-14, FR-223)
     * @param outsideServiceArea whether Customers &amp; Orders flagged its location as outside the
     *     service area, so it drops as <em>unreachable location</em> once the solve contract
     *     carries it (FR-320, FR-66, ADR-139); never for a location placed by hand or a missing
     *     location
     * @param window its time window on the date
     * @param weight its weight demand
     * @param volume its volume demand
     * @param serviceTime its service time
     * @param priority its priority
     * @param leaveAtDoor whether it may be left at the door, so its window is a preference (FR-364)
     */
    public record Order(
            OrderId order,
            Attempt attempt,
            Optional<GeoPoint> location,
            boolean outsideServiceArea,
            TimeWindow window,
            Weight weight,
            Volume volume,
            Duration serviceTime,
            Priority priority,
            boolean leaveAtDoor)
            implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public Order {
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(attempt, "attempt");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(weight, "weight");
            Objects.requireNonNull(volume, "volume");
            Objects.requireNonNull(serviceTime, "serviceTime");
            Objects.requireNonNull(priority, "priority");
        }

        /**
         * A form that hides the location.
         *
         * @return {@code Order[redacted]}
         */
        @Override
        public String toString() {
            return "Order[redacted]";
        }
    }

    /**
     * One depot, with its opening hours (FR-129, FR-195). Its location is a place a person may be
     * found at, so it prints redacted, as Fleet's {@code DepotFact} does.
     *
     * <p>To Be Implemented.
     *
     * @param depot the depot
     * @param location where it is, or empty, so its vehicles are left out (FR-197)
     * @param hours its opening hours, or empty when it sets none
     */
    public record Depot(DepotId depot, Optional<GeoPoint> location, Optional<Hours> hours) implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public Depot {
            Objects.requireNonNull(depot, "depot");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(hours, "hours");
        }

        /**
         * A form that hides the location.
         *
         * @return {@code Depot[redacted]}
         */
        @Override
        public String toString() {
            return "Depot[redacted]";
        }
    }

    /**
     * A depot's opening hours, as Israel wall-clock times on the date (FR-129, ADR-088).
     *
     * <p>To Be Implemented.
     *
     * @param opens when routes may first leave
     * @param closes when routes must be back
     */
    public record Hours(LocalTime opens, LocalTime closes) {

        /** Checks both times are present. */
        public Hours {
            Objects.requireNonNull(opens, "opens");
            Objects.requireNonNull(closes, "closes");
        }
    }

    /**
     * One vehicle of the request (FR-05, BR-05).
     *
     * <p>To Be Implemented.
     *
     * @param vehicle the vehicle
     * @param weightCapacity the most kilograms it carries
     * @param volumeCapacity the most litres it carries
     * @param startDepot where its route starts
     * @param endDepot where its route ends
     */
    public record Vehicle(
            VehicleId vehicle, Weight weightCapacity, Volume volumeCapacity, DepotId startDepot, DepotId endDepot) {

        /** Checks every part is present. */
        public Vehicle {
            Objects.requireNonNull(vehicle, "vehicle");
            Objects.requireNonNull(weightCapacity, "weightCapacity");
            Objects.requireNonNull(volumeCapacity, "volumeCapacity");
            Objects.requireNonNull(startDepot, "startDepot");
            Objects.requireNonNull(endDepot, "endDepot");
        }
    }

    /**
     * One driver of the request: their shift on the date and the vehicle they drive (FR-54, BR-06).
     * The validator judges a route's shift by its vehicle's rostered driver (ADR-112), so an
     * evaluation gives each layout route's driver that route's vehicle.
     *
     * <p>To Be Implemented.
     *
     * @param driver the driver's account
     * @param shift their shift on the date
     * @param vehicle the vehicle they drive that date, or empty when they have none
     */
    public record Driver(AccountId driver, TimeWindow shift, Optional<VehicleId> vehicle) {

        /** Checks every part is present, possibly empty. */
        public Driver {
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(shift, "shift");
            Objects.requireNonNull(vehicle, "vehicle");
        }
    }

    /**
     * The planning settings the travel model and the objective take, and the configured values that
     * every solve, evaluation and re-optimization takes (FR-321, FR-322, FR-408, FR-227, FR-21,
     * FR-67, FR-226, BR-120, FR-81, BR-105, ADR-121). A re-optimization's request adds its own two
     * values (see {@code ReoptimizationRequest}).
     *
     * <p>The optimizer applies the departure allowance. The core sends it as it is and never folds
     * it into the shift times, so the Python {@code ProblemFactory} alone decides how a route's
     * start and its planned times take it (ADR-050, ADR-121). The factory has no input for it yet:
     * a domain gap that the solve queue closes, for the daily solve and the re-optimization.
     *
     * <p>To Be Implemented.
     *
     * @param buckets the travel-time buckets and their factors
     * @param buffer the travel-time buffer
     * @param fixedVehicleCost the fixed cost per vehicle used, or off
     * @param departureAllowance the time between a shift's start and leaving the depot, which the
     *     optimizer applies (FR-67, FR-226, ADR-121)
     * @param doorWindowRate the door-window rate, from {@link SolveConfiguration} (BR-120, ADR-121)
     * @param breaksReserved whether routes reserve time for breaks, from {@link SolveConfiguration}
     *     (FR-81, BR-105, ADR-121)
     */
    public record TravelModel(
            TravelBuckets buckets,
            Buffer buffer,
            FixedVehicleCost fixedVehicleCost,
            DepartureAllowance departureAllowance,
            SolveConfiguration.Factor doorWindowRate,
            boolean breaksReserved) {

        /** Checks every part is present. */
        public TravelModel {
            Objects.requireNonNull(buckets, "buckets");
            Objects.requireNonNull(buffer, "buffer");
            Objects.requireNonNull(fixedVehicleCost, "fixedVehicleCost");
            Objects.requireNonNull(departureAllowance, "departureAllowance");
            Objects.requireNonNull(doorWindowRate, "doorWindowRate");
        }
    }
}
