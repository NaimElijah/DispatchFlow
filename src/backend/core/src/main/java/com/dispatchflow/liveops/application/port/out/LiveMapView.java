package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.delivery.DeliveryStatus;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.VehicleId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The dispatcher's live map of a date: each vehicle's latest position and how old it is, and each
 * stop's status (FR-33, FR-74, FR-34, FR-397). It holds positions and addresses' coordinates, so it
 * prints redacted.
 *
 * <p>Built in M5; the positions in M8.
 *
 * @param date the date shown
 * @param at the server's time the view was read at, which the positions' age counts to (FR-74)
 * @param routes the routes of the date's plan in force, in the plan's order
 */
public record LiveMapView(DeliveryDate date, Instant at, List<RouteOnMap> routes) implements PersonalData {

    /** Checks every part is present, and copies the routes. */
    public LiveMapView {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(at, "at");
        routes = List.copyOf(routes);
    }

    /**
     * A form that hides the positions.
     *
     * @return the date and the number of routes
     */
    @Override
    public String toString() {
        return "LiveMapView[date=" + date + ", routes=" + routes.size() + ", redacted]";
    }

    /**
     * One route on the map.
     *
     * <p>Built in M5.
     *
     * @param route the route
     * @param vehicle its vehicle
     * @param driver its current driver
     * @param run where its run stands (FR-244)
     * @param position its vehicle's latest kept position, or empty when none was received, which
     *     the map says (FR-74)
     * @param stops its stops, in planned order
     */
    public record RouteOnMap(
            RouteId route,
            VehicleId vehicle,
            AccountId driver,
            RunState run,
            Optional<VehiclePosition> position,
            List<StopOnMap> stops)
            implements PersonalData {

        /** Checks every part is present, possibly empty, and copies the stops. */
        public RouteOnMap {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(vehicle, "vehicle");
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(run, "run");
            Objects.requireNonNull(position, "position");
            stops = List.copyOf(stops);
        }

        /**
         * A form that hides the position.
         *
         * @return the route and its run, redacted
         */
        @Override
        public String toString() {
            return "RouteOnMap[route=" + route + ", run=" + run + ", redacted]";
        }
    }

    /**
     * A vehicle's latest position (FR-33, FR-74).
     *
     * <p>Built in M8.
     *
     * @param at where it was
     * @param receivedAt when the server received it, which its age counts from (FR-74)
     * @param stale whether it is older than 5 minutes by the server's clock (FR-74)
     */
    public record VehiclePosition(GeoPoint at, Instant receivedAt, boolean stale) implements PersonalData {

        /** Checks both instants and the point are present. */
        public VehiclePosition {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(receivedAt, "receivedAt");
        }

        /**
         * A form that hides the location.
         *
         * @return {@code VehiclePosition[redacted]}
         */
        @Override
        public String toString() {
            return "VehiclePosition[redacted]";
        }
    }

    /**
     * One stop on the map.
     *
     * <p>Built in M5.
     *
     * @param stop the stop
     * @param order its order
     * @param location its customer's coordinates, or empty when not known
     * @param status its delivery's status; {@link DeliveryStatus#PENDING} with nothing recorded
     * @param atRisk whether its projected arrival is past its window end less 15 minutes; never for a
     *     leave-at-door stop (FR-253, FR-369)
     * @param changedSincePublishing whether its order changed after publishing, until a
     *     re-optimization applies it or the stop has an outcome (FR-397)
     */
    public record StopOnMap(
            StopId stop,
            OrderId order,
            Optional<GeoPoint> location,
            DeliveryStatus status,
            boolean atRisk,
            boolean changedSincePublishing)
            implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public StopOnMap {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(location, "location");
            Objects.requireNonNull(status, "status");
        }

        /**
         * A form that hides the location.
         *
         * @return the stop and its status, redacted
         */
        @Override
        public String toString() {
            return "StopOnMap[stop=" + stop + ", status=" + status + ", redacted]";
        }
    }
}
