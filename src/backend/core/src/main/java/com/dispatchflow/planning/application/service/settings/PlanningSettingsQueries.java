package com.dispatchflow.planning.application.service.settings;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.GrantLimitExceeded;
import com.dispatchflow.planning.application.port.out.PlanningSettingsView;
import com.dispatchflow.planning.application.port.out.settings.SolversView;
import com.dispatchflow.planning.application.port.out.solvejob.RouteOptimizer;

/**
 * The planning settings an Admin sees and changes (FR-183, placement P7), and the solvers a dispatcher
 * may choose for a solve or a re-optimization (FR-20, FR-224). The optimizer lists only FR-20's
 * solvers, so the baseline's is never offered.
 *
 * <p>The settings are Admin and Dispatcher only (FR-183). The permission matrix's <em>Read the
 * company's settings</em> row grants a Driver only the company's name and dispatch phone. That grant
 * is limited, not empty, so the web adapter's role check lets a Driver through (ADR-068), and {@link
 * #settings(Actor)} refuses a Driver itself with {@link GrantLimitExceeded}.
 *
 * <p>{@link PlanningSettingsQueriesChange} holds the read-only transaction of each answer. The
 * solvers on offer are asked of {@link RouteOptimizer} before it (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class PlanningSettingsQueries {

    private final RouteOptimizer optimizer;
    private final PlanningSettingsQueriesChange change;

    /**
     * Creates the queries.
     *
     * @param optimizer the optimization service, for the solvers it offers
     * @param change the answers' read-only transactions
     */
    public PlanningSettingsQueries(RouteOptimizer optimizer, PlanningSettingsQueriesChange change) {
        this.optimizer = optimizer;
        this.change = change;
    }

    /**
     * The planning settings, or their starting values when an Admin never changed any. Only an Admin
     * or a Dispatcher reads them (FR-183).
     *
     * <p>To Be Implemented.
     *
     * @param actor the Admin or the Dispatcher
     * @return the settings, with their version, empty while never saved
     * @throws GrantLimitExceeded when the actor's roles give only the Driver's limited grant, which
     *     covers the company's name and dispatch phone and not these settings
     */
    public PlanningSettingsView settings(Actor actor) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * The solvers on offer, and the planning settings' default solver of each kind of solve (FR-20,
     * ADR-004).
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @return the solvers' names and the two defaults
     * @throws com.dispatchflow.planning.application.port.out.OptimizerUnavailable when the optimizer
     *     can't be reached
     */
    public SolversView solvers(Actor actor) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
