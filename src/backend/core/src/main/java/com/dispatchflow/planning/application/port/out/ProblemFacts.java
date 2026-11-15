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
 * the depots, the vehicles that may drive, the rostered drivers, the travel model's settings and the
 * matrix. {@code SolveRequestAssembler} builds it from Customers &amp; Orders' {@code OrdersForDate},
 * Fleet's {@code FleetFacts}, the planning settings and the stored matrix; the optimizer adapter turns
 * it into the {@code optimizer-api} request, and the Python {@code ProblemFactory} applies every rule
 * (DL §10.2, ADR-050).
 *
 * <p>Built in M0.
 *
 * @param date the delivery date
 * @param orders the date's uncancelled orders, each once, at their current attempt (BR-22, FR-258)
 * @param depots every depot a vehicle starts or ends at, each once
 * @param vehicles the vehicles that may drive on the date: active, and not marked unavailable
 *     (FR-56, FR-194)
 * @param drivers the date's rostered drivers who are available, each once (FR-54, FR-82)
 * @param travel the travel model's settings (FR-321, FR-322, FR-227, FR-21, FR-67)
 * @param matrix the base travel matrix over every distinct location of the orders and depots
 *     (ADR-052)
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
     * BR-53, BR-124, FR-364). Its location is a customer's place, so it prints redacted.
     *
     * <p>Built in M0.
     *
     * @param order the order
     * @param attempt its current attempt
     * @param location its customer's coordinates, or empty, so it drops as <em>missing location</em>
     *     (BR-14, FR-223)
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
     * <p>Built in M0.
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
     * <p>Built in M0.
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
     * One vehicle that may drive on the date (FR-05, BR-05).
     *
     * <p>Built in M0.
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
     * One rostered driver on the date: their shift and the vehicle they drive (FR-54, BR-06). The
     * validator judges a route's shift by its vehicle's rostered driver (ADR-112).
     *
     * <p>Built in M0.
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
     * The planning settings the travel model and the objective take (FR-321, FR-322, FR-408, FR-227,
     * FR-21, FR-67, FR-226).
     *
     * <p>Built in M0.
     *
     * @param buckets the travel-time buckets and their factors
     * @param buffer the travel-time buffer
     * @param fixedVehicleCost the fixed cost per vehicle used, or off
     * @param departureAllowance the time between a shift's start and leaving the depot
     */
    public record TravelModel(
            TravelBuckets buckets,
            Buffer buffer,
            FixedVehicleCost fixedVehicleCost,
            DepartureAllowance departureAllowance) {

        /** Checks every part is present. */
        public TravelModel {
            Objects.requireNonNull(buckets, "buckets");
            Objects.requireNonNull(buffer, "buffer");
            Objects.requireNonNull(fixedVehicleCost, "fixedVehicleCost");
            Objects.requireNonNull(departureAllowance, "departureAllowance");
        }
    }
}
