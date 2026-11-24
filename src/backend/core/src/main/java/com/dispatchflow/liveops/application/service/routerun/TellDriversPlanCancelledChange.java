package com.dispatchflow.liveops.application.service.routerun;

import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.planning.events.PlanCancelled;
import com.dispatchflow.shared.AccountId;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read-only transaction of {@link TellDriversPlanCancelled} (FR-337): it reads the driver of
 * each route the event names from Planning's {@link RouteMembership#driversAt}, at the
 * cancellation's time, so the notices go out after it.
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class TellDriversPlanCancelledChange {

    private final RouteMembership membership;

    /**
     * Creates the read.
     *
     * @param membership Planning's answers on a route's drivers at a time
     */
    public TellDriversPlanCancelledChange(RouteMembership membership) {
        this.membership = membership;
    }

    /**
     * The drivers who had a route on the cancelled plan.
     *
     * <p>To Be Implemented.
     *
     * @param event the cancelled plan
     * @return each driver once, however many of the plan's routes they drove
     */
    public List<AccountId> driversOf(PlanCancelled event) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
