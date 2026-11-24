package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import java.util.Objects;

/**
 * What changed on the live screens, which the event stream tells the open clients so they refetch
 * (DL §7.1, ADR-026). It names IDs only, never personal data (NFR-218).
 *
 * <p>The stream is filtered by role, and the Driver role gets its own published routes and nothing
 * else (ADR-035). So a case a driver's screen hears names the driver it is for, and the adapter sends
 * it to that driver's streams and to the staff's, without asking Planning who drives what. The web
 * app learns of a route change only this way (FR-347), and alerts a driver who has it open on their
 * route (FR-391).
 *
 * <p>To Be Implemented.
 */
public sealed interface LiveUpdate {

    /**
     * A stop's facts changed: an arrival, an outcome, a held report or a correction.
     *
     * <p>To Be Implemented.
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
     * <p>To Be Implemented.
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
     * <p>To Be Implemented.
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
     * <p>To Be Implemented.
     *
     * @param alert the alert
     */
    record AlertChanged(AlertId alert) implements LiveUpdate {

        /** Checks the alert is present. */
        public AlertChanged {
            Objects.requireNonNull(alert, "alert");
        }
    }

    /**
     * A date's routes were published, told once to each driver with a route on the plan, so a
     * driver with the web app open is alerted and the route is listed with its date (FR-152,
     * FR-324, FR-391).
     *
     * <p>To Be Implemented.
     *
     * @param date the plan's date, which the app opens
     * @param driver the driver it is for
     */
    record RoutesPublished(DeliveryDate date, AccountId driver) implements LiveUpdate {

        /** Checks both parts are present. */
        public RoutesPublished {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(driver, "driver");
        }
    }

    /**
     * A revision changed a route, told to its driver, and to the driver a hand-over took it from, so
     * the web app reads the route again and alerts the driver (FR-71, FR-347, FR-391). The
     * dispatcher's unconfirmed list follows it too (FR-250).
     *
     * <p>To Be Implemented.
     *
     * @param route the route
     * @param driver the driver it is for
     */
    record RouteRevised(RouteId route, AccountId driver) implements LiveUpdate {

        /** Checks both parts are present. */
        public RouteRevised {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(driver, "driver");
        }
    }

    /**
     * A date's plan was cancelled, told once to each driver who had a route on it (FR-337).
     *
     * <p>To Be Implemented.
     *
     * @param date the cancelled plan's date
     * @param driver the driver it is for
     */
    record PlanCancelled(DeliveryDate date, AccountId driver) implements LiveUpdate {

        /** Checks both parts are present. */
        public PlanCancelled {
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(driver, "driver");
        }
    }
}
