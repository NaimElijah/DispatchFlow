package com.dispatchflow.planning.application.service.settings;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.api.GrantLimitExceeded;
import com.dispatchflow.planning.application.port.out.PlanningSettingsView;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.planning.application.port.out.settings.SolversView;
import com.dispatchflow.planning.domain.plan.SolverName;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only transactions of {@link PlanningSettingsQueries}: the settings, and the default solvers
 * beside the ones the optimizer offers (FR-20, FR-183, TS §6.2, ADR-129).
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class PlanningSettingsQueriesChange {

    private final PlanningViews views;

    /**
     * Creates the transactions.
     *
     * @param views Planning's read side
     */
    public PlanningSettingsQueriesChange(PlanningViews views) {
        this.views = views;
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
     * The solvers on offer, with the planning settings' default solver of each kind of solve (FR-20,
     * ADR-004).
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param offered the solvers the optimizer offers, asked before the transaction
     * @return the solvers' names and the two defaults
     */
    public SolversView solvers(Actor actor, List<SolverName> offered) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
