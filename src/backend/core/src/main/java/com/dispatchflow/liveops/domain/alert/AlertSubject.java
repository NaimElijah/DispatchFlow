package com.dispatchflow.liveops.domain.alert;

import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What an alert is about: one record per {@link AlertKind}, naming the IDs it refers to (DL §4.5).
 * Each kind defines its own {@link DedupeKey} (ADR-107). Every subject names its plan, so an
 * unacknowledged alert stays visible until that plan completes (FR-261).
 */
public sealed interface AlertSubject {

    /**
     * The alert's kind.
     *
     * @return the kind this subject belongs to
     */
    AlertKind kind();

    /**
     * The plan the alert belongs to.
     *
     * @return the plan
     */
    PlanId plan();

    /**
     * The key that makes this alert once.
     *
     * @param alert the alert's own ID, which a kind that alerts on every report keys on
     * @return the key
     */
    DedupeKey dedupeKey(AlertId alert);

    /**
     * A stop projected to miss its window, under one revision (FR-34, FR-253). A new revision that
     * puts it at risk again alerts again.
     *
     * @param plan the plan
     * @param route the stop's route
     * @param stop the stop
     * @param revision the revision the projection used
     */
    record StopAtRisk(PlanId plan, RouteId route, StopId stop, RevisionNo revision) implements AlertSubject {

        /** Checks every part is present. */
        public StopAtRisk {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(revision, "revision");
        }

        @Override
        public AlertKind kind() {
            return AlertKind.STOP_AT_RISK;
        }

        @Override
        public DedupeKey dedupeKey(AlertId alert) {
            return DedupeKey.of(kind(), List.of(stop.value()), List.of(revision.value()));
        }
    }

    /**
     * A problem a driver reported (FR-95). Every report alerts, so the key is the alert's own ID.
     *
     * @param plan the plan
     * @param route the driver's route
     * @param driver the driver
     * @param problem what the problem is
     */
    record DriverProblemReported(PlanId plan, RouteId route, AccountId driver, DriverProblem problem)
            implements AlertSubject {

        /** Checks every part is present. */
        public DriverProblemReported {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(problem, "problem");
        }

        @Override
        public AlertKind kind() {
            return AlertKind.DRIVER_PROBLEM;
        }

        @Override
        public DedupeKey dedupeKey(AlertId alert) {
            return DedupeKey.of(kind(), List.of(alert.value()), List.of());
        }
    }

    /**
     * A route not started past its {@link StartDeadline} (FR-101).
     *
     * @param plan the plan
     * @param route the route
     */
    record RouteNotStarted(PlanId plan, RouteId route) implements AlertSubject {

        /** Checks every part is present. */
        public RouteNotStarted {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(route, "route");
        }

        @Override
        public AlertKind kind() {
            return AlertKind.ROUTE_NOT_STARTED;
        }

        @Override
        public DedupeKey dedupeKey(AlertId alert) {
            return DedupeKey.of(kind(), List.of(route.value()), List.of());
        }
    }

    /**
     * A stop a driver marked failed (FR-102).
     *
     * @param plan the plan
     * @param route the route that reported it
     * @param stop the stop
     */
    record StopFailed(PlanId plan, RouteId route, StopId stop) implements AlertSubject {

        /** Checks every part is present. */
        public StopFailed {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(stop, "stop");
        }

        @Override
        public AlertKind kind() {
            return AlertKind.STOP_FAILED;
        }

        @Override
        public DedupeKey dedupeKey(AlertId alert) {
            return DedupeKey.of(kind(), List.of(stop.value()), List.of());
        }
    }

    /**
     * A route change the driver hasn't confirmed by its {@link
     * com.dispatchflow.liveops.domain.routerun.ConfirmationDeadline} (FR-97, ADR-107). Each revision
     * alerts once.
     *
     * @param plan the plan
     * @param route the route
     * @param revision the unconfirmed revision
     */
    record RouteChangeUnconfirmed(PlanId plan, RouteId route, RevisionNo revision) implements AlertSubject {

        /** Checks every part is present. */
        public RouteChangeUnconfirmed {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(revision, "revision");
        }

        @Override
        public AlertKind kind() {
            return AlertKind.ROUTE_CHANGE_UNCONFIRMED;
        }

        @Override
        public DedupeKey dedupeKey(AlertId alert) {
            return DedupeKey.of(kind(), List.of(route.value()), List.of(revision.value()));
        }
    }

    /**
     * A late outcome accepted for a stop the plan had moved, dropped or cancelled (BR-89, BR-130,
     * DL §8).
     *
     * @param plan the plan
     * @param servedOn the route that served the stop
     * @param stop the stop
     * @param leftRoute the route the stop left, when it was on another driver's route
     */
    record LateReport(PlanId plan, RouteId servedOn, StopId stop, Optional<RouteId> leftRoute) implements AlertSubject {

        /** Checks every part is present. */
        public LateReport {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(servedOn, "servedOn");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(leftRoute, "leftRoute");
        }

        @Override
        public AlertKind kind() {
            return AlertKind.LATE_REPORT;
        }

        @Override
        public DedupeKey dedupeKey(AlertId alert) {
            return DedupeKey.of(kind(), List.of(stop.value()), List.of());
        }
    }

    /**
     * A late report held for the dispatcher's decision (BR-132). Each held report alerts once.
     *
     * @param plan the plan
     * @param route the route the report came from
     * @param stop the stop
     * @param report the held report
     */
    record ReportHeld(PlanId plan, RouteId route, StopId stop, HeldReportId report) implements AlertSubject {

        /** Checks every part is present. */
        public ReportHeld {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(report, "report");
        }

        @Override
        public AlertKind kind() {
            return AlertKind.REPORT_HELD;
        }

        @Override
        public DedupeKey dedupeKey(AlertId alert) {
            return DedupeKey.of(kind(), List.of(report.value()), List.of());
        }
    }

    /**
     * A report refused and shown to the dispatcher (BR-90, BR-134, BR-135): one per stop, reporting
     * route, reporting driver and refused fact, so a retried refusal alerts once and a different
     * driver's or a different fact's refusal alerts again. An arrival refused for a cancellation
     * alerts too (ADR-107, ADR-114).
     *
     * @param plan the plan
     * @param route the route the report came from
     * @param stop the stop
     * @param driver the driver who sent the report
     * @param refused what the report stated: an arrival or an outcome
     */
    record ReportRefused(PlanId plan, RouteId route, StopId stop, AccountId driver, RefusedFact refused)
            implements AlertSubject {

        /** Checks every part is present. */
        public ReportRefused {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(refused, "refused");
        }

        @Override
        public AlertKind kind() {
            return AlertKind.REPORT_REFUSED;
        }

        @Override
        public DedupeKey dedupeKey(AlertId alert) {
            return DedupeKey.of(
                    kind(), List.of(stop.value(), route.value(), driver.value()), List.of(), List.of(refused));
        }
    }

    /**
     * A route finished with stops pending (FR-329).
     *
     * @param plan the plan
     * @param route the route
     */
    record RouteFinishedWithPendingStops(PlanId plan, RouteId route) implements AlertSubject {

        /** Checks every part is present. */
        public RouteFinishedWithPendingStops {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(route, "route");
        }

        @Override
        public AlertKind kind() {
            return AlertKind.ROUTE_FINISHED_WITH_PENDING_STOPS;
        }

        @Override
        public DedupeKey dedupeKey(AlertId alert) {
            return DedupeKey.of(kind(), List.of(route.value()), List.of());
        }
    }

    /**
     * A rating from 1 to 4, which Messaging raises through {@code RaiseAlert} (FR-361).
     *
     * @param plan the plan
     * @param stop the rated stop
     * @param request the rating request
     */
    record LowRating(PlanId plan, StopId stop, RatingRequestId request) implements AlertSubject {

        /** Checks every part is present. */
        public LowRating {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(request, "request");
        }

        @Override
        public AlertKind kind() {
            return AlertKind.LOW_RATING;
        }

        @Override
        public DedupeKey dedupeKey(AlertId alert) {
            return DedupeKey.of(kind(), List.of(request.value()), List.of());
        }
    }
}
