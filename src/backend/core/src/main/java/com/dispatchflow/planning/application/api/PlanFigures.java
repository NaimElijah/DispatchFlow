package com.dispatchflow.planning.application.api;

import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.application.NotFound;

/**
 * What Reporting reads about a published plan (DL §2.2, FR-38, FR-39, FR-338, FR-342, ADR-109): its
 * planned figures, its standing, and the inputs its baseline is solved from (placement P5).
 *
 * <p>Built in M5.
 */
public interface PlanFigures {

    /**
     * The plan's planned figures, from its current revision (FR-38, FR-39, FR-266).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return its distance, driver-hours and every order's planned service
     * @throws NotFound when no published plan has the ID
     */
    PlannedFiguresFact figuresOf(PlanId plan);

    /**
     * Where the plan is now, which decides whether it is compared with its baseline (FR-338,
     * ADR-109 reading 10).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return published, completed or cancelled
     * @throws NotFound when no published plan has the ID
     */
    PlanStandingFact standingOf(PlanId plan);

    /**
     * What the baseline's nearest-neighbour solve of the plan's day is built from (FR-39, FR-271,
     * FR-342, ADR-052).
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return the plan's orders and the fleet its daily solve was given
     * @throws NotFound when no published plan has the ID
     */
    BaselineInputsFact inputsOf(PlanId plan);
}
