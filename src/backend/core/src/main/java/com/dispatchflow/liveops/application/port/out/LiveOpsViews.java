package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.liveops.domain.delivery.DeliveryStatus;
import com.dispatchflow.liveops.domain.delivery.StopFact;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Live Operations' read port: what its own schema answers for the screens, read without loading the
 * aggregates (DL §13). The routes and stops come from Planning's {@code PublishedRoutes} and the
 * orders' fields from Customers &amp; Orders; the query classes join them with these rows.
 *
 * <p>Built in M5 and M8, method by method.
 */
public interface LiveOpsViews {

    /**
     * Where several stops stand.
     *
     * <p>Built in M5.
     *
     * @param stops the stops, each once
     * @return one row per stop that has a delivery; a pending stop is left out
     */
    List<StopRow> stops(List<StopId> stops);

    /**
     * Where several routes' runs stand.
     *
     * <p>Built in M5.
     *
     * @param routes the routes, each once
     * @return one row per route that has a run; a route left out has not started
     */
    List<RunRow> runs(List<RouteId> routes);

    /**
     * Each route's latest kept position (FR-33, FR-74).
     *
     * <p>Built in M8.
     *
     * @param routes the routes, each once
     * @return one position per route that kept any
     */
    List<LatestPosition> latestPositions(List<RouteId> routes);

    /**
     * A plan's unacknowledged alerts (FR-157, FR-261).
     *
     * <p>Built in M8.
     *
     * @param plan the plan
     * @return the alerts, the newest first
     */
    List<OpenAlertsView.OpenAlert> unacknowledgedOf(PlanId plan);

    /**
     * The open held reports of several stops (BR-132).
     *
     * <p>Built in M8.
     *
     * @param stops the stops, each once
     * @return the reports, in the order they were held
     */
    List<HeldReportsView.HeldEntry> openHeldOf(List<StopId> stops);

    /**
     * One stop's history (FR-333).
     *
     * <p>Built in M8.
     *
     * @param stop the stop
     * @return the history, or empty when nothing is recorded for the stop
     */
    Optional<StopHistoryView> historyOf(StopId stop);

    /**
     * Where one stop stands.
     *
     * <p>Built in M5.
     *
     * @param stop the stop
     * @param status its delivery's status
     * @param fact its arrival and effective outcome times, which projections read (DL §10.4)
     */
    record StopRow(StopId stop, DeliveryStatus status, StopFact fact) {

        /** Checks every part is present. */
        public StopRow {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(fact, "fact");
        }
    }

    /**
     * Where one route's run stands.
     *
     * <p>Built in M5.
     *
     * @param route the route
     * @param state in progress or finished
     * @param startedAt when it started, corrected for the device's clock (BR-114)
     * @param confirmed the last revision the driver confirmed, or empty when none (FR-97)
     * @param lastReportAt when the latest report on the route happened, or empty when none arrived
     *     (FR-158)
     */
    record RunRow(
            RouteId route,
            RunState state,
            Instant startedAt,
            Optional<RevisionNo> confirmed,
            Optional<Instant> lastReportAt) {

        /** Checks every part is present, possibly empty. */
        public RunRow {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(startedAt, "startedAt");
            Objects.requireNonNull(confirmed, "confirmed");
            Objects.requireNonNull(lastReportAt, "lastReportAt");
        }
    }

    /**
     * A route's latest kept position. It is a person's location, so it prints redacted.
     *
     * <p>Built in M8.
     *
     * @param route the route
     * @param at where the device was
     * @param receivedAt when the server received it (FR-74)
     */
    record LatestPosition(RouteId route, GeoPoint at, Instant receivedAt) implements PersonalData {

        /** Checks every part is present. */
        public LatestPosition {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(receivedAt, "receivedAt");
        }

        /**
         * A form that hides the location.
         *
         * @return the route, redacted
         */
        @Override
        public String toString() {
            return "LatestPosition[route=" + route + ", redacted]";
        }
    }
}
