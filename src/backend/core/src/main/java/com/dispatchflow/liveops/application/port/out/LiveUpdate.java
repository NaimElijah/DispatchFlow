package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.util.Objects;

/**
 * What changed on the live screens, which the event stream tells the open clients so they refetch
 * (DL §7.1, ADR-026). It names IDs only, never personal data (NFR-218).
 *
 * <p>Built in M5 and M8, case by case.
 */
public sealed interface LiveUpdate {

    /**
     * A stop's facts changed: an arrival, an outcome, a held report or a correction.
     *
     * <p>Built in M5.
     *
     * @param stop the stop
     */
    record StopChanged(StopId stop) implements LiveUpdate {

        /** Checks the stop is present. */
        public StopChanged {
            Objects.requireNonNull(stop, "stop");
        }
    }

    /**
     * A route's run changed: started, finished, or a revision confirmed.
     *
     * <p>Built in M5.
     *
     * @param route the route
     */
    record RunChanged(RouteId route) implements LiveUpdate {

        /** Checks the route is present. */
        public RunChanged {
            Objects.requireNonNull(route, "route");
        }
    }

    /**
     * A route kept new positions (FR-33).
     *
     * <p>Built in M8.
     *
     * @param route the route
     */
    record PositionsKept(RouteId route) implements LiveUpdate {

        /** Checks the route is present. */
        public PositionsKept {
            Objects.requireNonNull(route, "route");
        }
    }

    /**
     * An alert was raised or acknowledged (FR-157, FR-260, FR-405).
     *
     * <p>Built in M8.
     *
     * @param alert the alert
     */
    record AlertChanged(AlertId alert) implements LiveUpdate {

        /** Checks the alert is present. */
        public AlertChanged {
            Objects.requireNonNull(alert, "alert");
        }
    }
}
