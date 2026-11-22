package com.dispatchflow.liveops.application.port.out.routerun;

import com.dispatchflow.liveops.application.port.out.RunState;
import com.dispatchflow.liveops.domain.delivery.DeliveryStatus;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.PlainText;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A driver's own routes of a date: the stops in order with their projected arrivals, windows,
 * customer details and notes (FR-27, FR-96, FR-154, FR-251, FR-324, placement P6). It holds the
 * recipients' details, so it prints redacted.
 *
 * <p>Built in M5; the projected arrivals in M8.
 *
 * @param date the date
 * @param driver the driver, the only account it is read for (BR-41)
 * @param routes the driver's routes of the date, in the plan's order; empty when they have none
 */
public record DriverDayView(DeliveryDate date, AccountId driver, List<DriverRoute> routes) implements PersonalData {

    /** Checks every part is present, and copies the routes. */
    public DriverDayView {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(driver, "driver");
        routes = List.copyOf(routes);
    }

    /**
     * A form that leaves the stops' details out.
     *
     * @return the date and the number of routes
     */
    @Override
    public String toString() {
        return "DriverDayView[date=" + date + ", routes=" + routes.size() + ", redacted]";
    }

    /**
     * One of the driver's routes.
     *
     * <p>Built in M5.
     *
     * @param route the route
     * @param vehicle its vehicle
     * @param planRevision the plan's current revision, which the phone confirms after a change
     *     (FR-97)
     * @param run where its run stands (FR-244)
     * @param stops its stops, in planned order
     */
    public record DriverRoute(RouteId route, VehicleId vehicle, int planRevision, RunState run, List<DriverStop> stops)
            implements PersonalData {

        /** Checks every part is present, and copies the stops. */
        public DriverRoute {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(vehicle, "vehicle");
            Objects.requireNonNull(run, "run");
            stops = List.copyOf(stops);
        }

        /**
         * A form that leaves the stops' details out.
         *
         * @return the route and its run, redacted
         */
        @Override
        public String toString() {
            return "DriverRoute[route=" + route + ", run=" + run + ", redacted]";
        }
    }

    /**
     * One stop of the driver's route.
     *
     * <p>Built in M5.
     *
     * @param stop the stop
     * @param order its order
     * @param status its delivery's status
     * @param eta its projected arrival, or its planned one before the route moves (FR-27, FR-96,
     *     FR-254); empty when it has an outcome or is not timed
     * @param window its time window, whose end the screen counts down to (FR-154)
     * @param location the stop's stored coordinates, which Navigate sends to Waze or Google Maps,
     *     so a location placed by hand is honoured (FR-28, FR-325); empty when they are not known
     * @param leaveAtDoor whether it may be left at the door (FR-364)
     * @param atRisk whether it is at risk, marked as on the dispatcher's list (FR-251)
     * @param details the customer's details and the notes the driver reads (FR-27, FR-86, FR-87,
     *     FR-135)
     */
    public record DriverStop(
            StopId stop,
            OrderId order,
            DeliveryStatus status,
            Optional<Instant> eta,
            TimeWindow window,
            Optional<GeoPoint> location,
            boolean leaveAtDoor,
            boolean atRisk,
            StopDetails details)
            implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public DriverStop {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(eta, "eta");
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(details, "details");
        }

        /**
         * A form that leaves the details and the location out.
         *
         * @return the stop and its status, redacted
         */
        @Override
        public String toString() {
            return "DriverStop[stop=" + stop + ", status=" + status + ", redacted]";
        }
    }

    /**
     * What a driver reads at a stop, from Customers &amp; Orders' {@code StopOrders}; a field
     * anonymization removed is empty (FR-86, FR-87, FR-135, NFR-257).
     *
     * <p>Built in M5.
     *
     * @param address the customer's address as typed
     * @param floor the order's floor
     * @param apartment the order's apartment
     * @param entranceCode the order's entrance code
     * @param orderNote the order's delivery note
     * @param customerNote the customer's standing notes for drivers
     * @param recipientName the order's recipient name
     * @param recipientPhone the order's recipient phone
     * @param customerName the customer's contact name
     * @param customerPhone the customer's contact phone
     */
    public record StopDetails(
            Optional<PlainText> address,
            Optional<PlainText> floor,
            Optional<PlainText> apartment,
            Optional<PlainText> entranceCode,
            Optional<PlainText> orderNote,
            Optional<PlainText> customerNote,
            Optional<PlainText> recipientName,
            Optional<PhoneNumber> recipientPhone,
            Optional<PlainText> customerName,
            Optional<PhoneNumber> customerPhone)
            implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public StopDetails {
            Objects.requireNonNull(address, "address");
            Objects.requireNonNull(floor, "floor");
            Objects.requireNonNull(apartment, "apartment");
            Objects.requireNonNull(entranceCode, "entranceCode");
            Objects.requireNonNull(orderNote, "orderNote");
            Objects.requireNonNull(customerNote, "customerNote");
            Objects.requireNonNull(recipientName, "recipientName");
            Objects.requireNonNull(recipientPhone, "recipientPhone");
            Objects.requireNonNull(customerName, "customerName");
            Objects.requireNonNull(customerPhone, "customerPhone");
        }

        /**
         * A form that hides the person's data.
         *
         * @return {@code StopDetails[redacted]}
         */
        @Override
        public String toString() {
            return "StopDetails[redacted]";
        }
    }
}
