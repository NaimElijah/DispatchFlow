package com.dispatchflow.liveops.application.service;

import com.dispatchflow.shared.RouteId;
import java.util.Objects;

/**
 * A driver finished a route that has no run, so the finish is refused as <em>not started</em>
 * (ADR-107, FR-244). The domain has no error for it, because a route with no run has no {@code
 * RouteRun} to refuse it, so it is no {@code DomainError}: the web adapter answers it with 409 and
 * {@link #code()}. A route a plan in force doesn't hold is {@code NotFound} instead.
 *
 * <p>Built in M5.
 */
public final class RouteNotStarted extends RuntimeException {

    /** The stable code the web adapter answers with, beside the 409 status (ADR-107). */
    public static final String CODE = "ROUTE_NOT_STARTED";

    private final RouteId route;

    /**
     * Creates the error.
     *
     * @param route the route the driver tried to finish
     */
    public RouteNotStarted(RouteId route) {
        super("route " + Objects.requireNonNull(route, "route").value() + " has not started");
        this.route = route;
    }

    /**
     * The stable code.
     *
     * @return {@value #CODE}
     */
    public String code() {
        return CODE;
    }

    /**
     * The route, for the problem's {@code params}.
     *
     * @return the route
     */
    public RouteId route() {
        return route;
    }
}
