package com.dispatchflow.planning.application.service.settings;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.planning.application.port.out.PlanningSettingsView;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.planning.application.port.out.SolversView;
import com.dispatchflow.planning.application.port.out.solvejob.RouteOptimizer;

/**
 * The planning settings an Admin sees and changes (FR-183, placement P7), and the solvers a dispatcher
 * may choose for a solve or a re-optimization (FR-20, FR-224).
 *
 * <p>Built in M4.
 */
public final class PlanningSettingsQueries {

    private final PlanningViews views;
    private final RouteOptimizer optimizer;

    /**
     * Creates the queries.
     *
     * @param views Planning's read side
     * @param optimizer the optimization service, for the solvers it offers
     */
    public PlanningSettingsQueries(PlanningViews views, RouteOptimizer optimizer) {
        this.views = views;
        this.optimizer = optimizer;
    }

    /**
     * The planning settings, or their starting values when an Admin never changed any.
     *
     * <p>Built in M4.
     *
     * @param actor the Admin
     * @return the settings, with their version
     */
    public PlanningSettingsView settings(Actor actor) {
        throw new UnsupportedOperationException("Built in M4");
    }

    /**
     * The solvers on offer, and the one a request without a solver runs (placement P3).
     *
     * <p>Built in M4.
     *
     * @param actor the dispatcher
     * @return the solvers' names and the default
     * @throws java.io.UncheckedIOException when the optimizer can't be reached
     */
    public SolversView solvers(Actor actor) {
        throw new UnsupportedOperationException("Built in M4");
    }
}
