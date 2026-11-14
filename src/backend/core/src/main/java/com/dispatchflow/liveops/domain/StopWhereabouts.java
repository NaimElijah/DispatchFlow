package com.dispatchflow.liveops.domain;

import com.dispatchflow.shared.RouteId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Where a stop is now, from Planning's {@code RouteMembership}, compared with the route a report
 * came from (DL §8, BR-132). The §8 table is keyed on the route that held the stop at the report's
 * time, which the {@link Entitlement} confirms.
 */
public sealed interface StopWhereabouts {

    /**
     * The cancellation a report timed before it would override (BR-130, BR-132).
     *
     * @param time the report's corrected time
     * @return the cancellation's kind, when the stop was cancelled after that time
     */
    default Optional<CancellationKind> cancelledAfter(CorrectedTime time) {
        return Optional.empty();
    }

    /** Still on the route the report came from. */
    record OnServingRoute() implements StopWhereabouts {}

    /**
     * On another driver's route now (BR-89).
     *
     * @param route that route
     */
    record OnAnotherRoute(RouteId route) implements StopWhereabouts {

        /** Checks the route is present. */
        public OnAnotherRoute {
            Objects.requireNonNull(route, "route");
        }
    }

    /** Dropped by a later re-optimization, or on no route for another reason (DL §8). */
    record Dropped() implements StopWhereabouts {}

    /**
     * Gone because its order was cancelled or rescheduled, or its plan cancelled (BR-88, BR-130,
     * ADR-107).
     *
     * @param kind what happened
     * @param at when it happened
     */
    record Cancelled(CancellationKind kind, Instant at) implements StopWhereabouts {

        /** Checks both parts are present. */
        public Cancelled {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(at, "at");
        }

        /**
         * Whether a report's time is at or after the cancellation, so the report is refused (BR-88,
         * BR-90). A report at the cancellation's instant is not before it (ADR-107).
         *
         * @param time the report's corrected time
         * @return {@code true} when the corrected time is not before {@link #at()}
         */
        public boolean isNotAfter(CorrectedTime time) {
            return !time.corrected().isBefore(at);
        }

        @Override
        public Optional<CancellationKind> cancelledAfter(CorrectedTime time) {
            return isNotAfter(time) ? Optional.empty() : Optional.of(kind);
        }
    }
}
