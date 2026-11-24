package com.dispatchflow.liveops.application.port.out.routerun;

import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RouteId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The drivers who have not confirmed a change to their route (FR-97, FR-250). A revision that took
 * effect before a run started counts as seen (ADR-107).
 *
 * <p>To Be Implemented.
 *
 * @param date the date
 * @param routes the routes whose last change waits for its driver's confirmation, in the plan's order
 */
public record UnconfirmedView(DeliveryDate date, List<UnconfirmedRoute> routes) {

    /** Checks the date is present, and copies the routes. */
    public UnconfirmedView {
        Objects.requireNonNull(date, "date");
        routes = List.copyOf(routes);
    }

    /**
     * One route whose change is unconfirmed.
     *
     * <p>To Be Implemented.
     *
     * @param route the route
     * @param driver its current driver
     * @param changedIn the revision that last changed it
     * @param changedAt when that revision took effect
     * @param confirmed the last revision the driver confirmed, or empty when none
     * @param overdue whether the confirmation is 10 minutes or more late, so the dispatcher was
     *     alerted (FR-157, ADR-107)
     */
    public record UnconfirmedRoute(
            RouteId route,
            AccountId driver,
            RevisionNo changedIn,
            Instant changedAt,
            Optional<RevisionNo> confirmed,
            boolean overdue) {

        /** Checks every part is present, possibly empty. */
        public UnconfirmedRoute {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(changedIn, "changedIn");
            Objects.requireNonNull(changedAt, "changedAt");
            Objects.requireNonNull(confirmed, "confirmed");
        }
    }
}
