package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DepotId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PlanId;
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
 * What the optimizer's nearest-neighbour baseline of a plan is solved from: the plan's orders and
 * fleet as its daily solve was given them, so both sides cover the same orders and fleet (FR-39,
 * FR-271, FR-342, ADR-052, ADR-109). {@link ReportingViews#baselineInputsOf} reads it from
 * Planning's and Customers &amp; Orders' published views (ADR-041). The orders' and the depots'
 * locations are personal data, so it prints redacted.
 *
 * <p>Built in M5.
 *
 * @param plan the plan
 * @param date the plan's delivery date
 * @param orders the orders of the plan's current revision, served or dropped, each once, less those
 *     cancelled now (BR-30)
 * @param vehicles the vehicles the plan's daily solve was given, each once
 * @param depots every depot those vehicles start or end at, each once
 */
public record BaselineInputs(
        PlanId plan, DeliveryDate date, List<Order> orders, List<Vehicle> vehicles, List<Depot> depots)
        implements PersonalData {

    /** Checks the plan and the date are present, and copies the lists. */
    public BaselineInputs {
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(date, "date");
        orders = List.copyOf(orders);
        vehicles = List.copyOf(vehicles);
        depots = List.copyOf(depots);
    }

    /**
     * A form that hides the orders' and the depots' locations.
     *
     * @return {@code BaselineInputs[redacted]}
     */
    @Override
    public String toString() {
        return "BaselineInputs[redacted]";
    }

    /**
     * One order the baseline is solved on, as Customers &amp; Orders' published view gives it (FR-342,
     * BR-119). Its location is a kernel {@link GeoPoint}, so it prints redacted.
     *
     * <p>Built in M5.
     *
     * @param order the order
     * @param location its customer's coordinates, or empty when they are not known, so the order
     *     drops as missing location (BR-14)
     * @param window its time window on the date, its own or its customer's default (BR-49)
     * @param weight its weight demand (BR-50)
     * @param volume its volume demand (BR-50)
     * @param serviceTime its service time, its own or its customer's default (BR-04, BR-124)
     * @param priority its priority (BR-53)
     * @param leaveAtDoor whether it may be left at the door, so its window is a preference (BR-119)
     */
    public record Order(
            OrderId order,
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
     * One vehicle of the fleet the plan's daily solve was given, as Planning's published view gives
     * it (FR-39, FR-271, FR-342): what it carries, its depots and its driver's shift.
     *
     * <p>Built in M5.
     *
     * @param vehicle the vehicle
     * @param weightCapacity the most kilograms it carries
     * @param volumeCapacity the most litres it carries
     * @param startDepot where its route starts
     * @param endDepot where its route ends
     * @param shift its driver's shift on the plan's date, whole (FR-271)
     */
    public record Vehicle(
            VehicleId vehicle,
            Weight weightCapacity,
            Volume volumeCapacity,
            DepotId startDepot,
            DepotId endDepot,
            TimeWindow shift) {

        /** Checks every part is present. */
        public Vehicle {
            Objects.requireNonNull(vehicle, "vehicle");
            Objects.requireNonNull(weightCapacity, "weightCapacity");
            Objects.requireNonNull(volumeCapacity, "volumeCapacity");
            Objects.requireNonNull(startDepot, "startDepot");
            Objects.requireNonNull(endDepot, "endDepot");
            Objects.requireNonNull(shift, "shift");
        }
    }

    /**
     * One depot a vehicle starts or ends at, as Planning's published view gives the daily solve's
     * depot (FR-39, FR-342, BR-46). Its location is a kernel {@link GeoPoint}, so it prints redacted.
     *
     * <p>Built in M5.
     *
     * @param depot the depot
     * @param location where it is
     * @param hours its opening hours, or empty when it sets none
     */
    public record Depot(DepotId depot, GeoPoint location, Optional<Hours> hours) implements PersonalData {

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
     * A depot's opening hours, as Israel wall-clock times on every date (FR-129, ADR-103).
     *
     * <p>Built in M5.
     *
     * @param opens when routes may first leave
     * @param closes when routes must be back, after {@code opens}
     */
    public record Hours(LocalTime opens, LocalTime closes) {

        /** Checks both times are present. */
        public Hours {
            Objects.requireNonNull(opens, "opens");
            Objects.requireNonNull(closes, "closes");
        }
    }
}
