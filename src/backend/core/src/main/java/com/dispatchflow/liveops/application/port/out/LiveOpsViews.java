package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.liveops.domain.delivery.DeliveryStatus;
import com.dispatchflow.liveops.domain.delivery.StopFact;
import com.dispatchflow.liveops.domain.proof.ProofId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PersonalData;
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
 * <p>To Be Implemented.
 */
public interface LiveOpsViews {

    /**
     * Where several stops stand.
     *
     * <p>To Be Implemented.
     *
     * @param stops the stops, each once
     * @return one row per stop that has a delivery; a pending stop is left out
     */
    List<StopRow> stops(List<StopId> stops);

    /**
     * Where several routes' runs stand.
     *
     * <p>To Be Implemented.
     *
     * @param routes the routes, each once
     * @return one row per route that has a run; a route left out has not started
     */
    List<RunRow> runs(List<RouteId> routes);

    /**
     * Each route's latest kept position (FR-33, FR-74).
     *
     * <p>To Be Implemented.
     *
     * @param routes the routes, each once
     * @return one position per route that kept any
     */
    List<LatestPosition> latestPositions(List<RouteId> routes);

    /**
     * Every unacknowledged alert the dispatcher's list shows on a day, whatever its plan's date
     * (FR-157, FR-261): one whose plan has not completed, and one raised after its plan completed.
     * A plan completes at the midnight after its date (BR-102), so the read needs no answer from
     * Planning: it compares the plan's date kept beside each alert ({@link
     * AlertRepository#addUnlessKeyTaken}) with today. An unacknowledged alert raised before its plan
     * completed drops off the list then.
     *
     * <p>To Be Implemented.
     *
     * @param today today's date in {@code Asia/Jerusalem} (ADR-088)
     * @return the alerts, the newest first; one whose plan's date is today or later, or one raised
     *     at or after the midnight that ended its plan's date
     */
    List<OpenAlertsView.OpenAlert> unacknowledgedAt(DeliveryDate today);

    /**
     * Every open held report, whatever its stop's date: a held report is a late one, so it waits
     * for a decision after its plan completes (BR-132, BR-135).
     *
     * <p>To Be Implemented.
     *
     * @return the reports, in the order they were held
     */
    List<HeldReportsView.HeldEntry> openHeld();

    /**
     * One stop's history (FR-333).
     *
     * <p>To Be Implemented.
     *
     * @param stop the stop
     * @return the history, or empty when nothing is recorded for the stop
     */
    Optional<StopHistoryView> historyOf(StopId stop);

    /**
     * The stop whose delivery names a proof, so a query can check whose route the proof is on
     * (BR-02, NFR-38). The delivery keeps the proof's ID after retention deletes the file
     * (ADR-107).
     *
     * <p>To Be Implemented.
     *
     * @param proof the proof's ID
     * @return the stop, or empty when no delivery names the proof
     */
    Optional<StopId> stopOfProof(ProofId proof);

    /**
     * Where one stop stands.
     *
     * <p>To Be Implemented.
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
     * <p>To Be Implemented.
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
     * <p>To Be Implemented.
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
