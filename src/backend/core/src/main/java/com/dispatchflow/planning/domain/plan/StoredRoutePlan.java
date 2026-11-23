package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.solution.RoutePlan;
import com.dispatchflow.planning.domain.solution.TravelLeg;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.Embeddable;
import java.time.Duration;

/**
 * The columns one {@link RoutePlan} is stored in, inside its route's row (ADR-127 decision 3): the
 * date its departure and return count from, each one's seconds, and the leg into the route's end.
 * {@link RoutePlan} keeps both times on one date, so the date is stored once. A route with no plan
 * stores none of these columns, which Hibernate reads back as no embedded value, and the table's
 * {@code CHECK} keeps them all or none. Its values check rules, so {@link #routePlan()} rebuilds them
 * through {@link StoredStateInvalid#whileRestoring}. A solved route is stored this way; it is
 * package-private, not nested, so that every aggregate of this package can.
 */
@Embeddable
class StoredRoutePlan {

    private DeliveryDate timesDate;

    private long leavesAtSeconds;

    private long returnsAtSeconds;

    private Duration legToEndTravel;

    private long legToEndMetres;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredRoutePlan() {}

    /**
     * Stores a route's plan.
     *
     * @param plan the plan
     */
    StoredRoutePlan(RoutePlan plan) {
        this.timesDate = plan.leavesAt().date();
        this.leavesAtSeconds = plan.leavesAt().seconds();
        this.returnsAtSeconds = plan.returnsAt().seconds();
        this.legToEndTravel = plan.legToEnd().travel();
        this.legToEndMetres = plan.legToEnd().metres();
    }

    /**
     * The route's plan these columns store.
     *
     * @return the plan
     * @throws StoredStateInvalid when its times are before the day or out of order, or its leg is
     *     negative
     */
    RoutePlan routePlan() {
        return StoredStateInvalid.whileRestoring(
                RoutePlan.class,
                () -> new RoutePlan(
                        SolveTime.of(timesDate, leavesAtSeconds),
                        SolveTime.of(timesDate, returnsAtSeconds),
                        TravelLeg.of(legToEndTravel, legToEndMetres)));
    }
}
