package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.SolveTime;
import com.dispatchflow.planning.domain.solution.StopPlan;
import com.dispatchflow.planning.domain.solution.StopTimes;
import com.dispatchflow.planning.domain.solution.TravelLeg;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.Embeddable;
import java.time.Duration;

/**
 * The columns one {@link StopPlan} is stored in, inside its stop's row (ADR-127 decision 3): the
 * date its three times count from, each time's seconds, and the leg into the stop. {@link StopTimes}
 * keeps its three times on one date, so the date is stored once. Its values check rules, so {@link
 * #stopPlan()} rebuilds them through {@link StoredStateInvalid#whileRestoring}. A solved stop is
 * stored this way; it is package-private, not nested, so that every aggregate of this package can.
 */
@Embeddable
class StoredStopPlan {

    private DeliveryDate timesDate;

    private long arrivalSeconds;

    private long startSeconds;

    private long departureSeconds;

    private Duration legInTravel;

    private long legInMetres;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredStopPlan() {}

    /**
     * Stores a stop's plan.
     *
     * @param plan the plan
     */
    StoredStopPlan(StopPlan plan) {
        this.timesDate = plan.times().arrival().date();
        this.arrivalSeconds = plan.times().arrival().seconds();
        this.startSeconds = plan.times().start().seconds();
        this.departureSeconds = plan.times().departure().seconds();
        this.legInTravel = plan.legIn().travel();
        this.legInMetres = plan.legIn().metres();
    }

    /**
     * The stop's plan these columns store.
     *
     * @return the plan
     * @throws StoredStateInvalid when its times are before the day or out of order, or its leg is
     *     negative
     */
    StopPlan stopPlan() {
        return StoredStateInvalid.whileRestoring(
                StopPlan.class,
                () -> new StopPlan(
                        StopTimes.of(
                                SolveTime.of(timesDate, arrivalSeconds),
                                SolveTime.of(timesDate, startSeconds),
                                SolveTime.of(timesDate, departureSeconds)),
                        TravelLeg.of(legInTravel, legInMetres)));
    }
}
