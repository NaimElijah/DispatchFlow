package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.planning.application.api.BaselineInputsFact;
import com.dispatchflow.planning.application.api.PlanFigures;
import com.dispatchflow.planning.application.api.PlanStandingFact;
import com.dispatchflow.planning.application.api.PlannedFiguresFact;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.shared.PlanId;

/**
 * Planning's implementation of {@link PlanFigures}: it reads a published plan's current revision, its
 * standing and its daily solve's stored inputs through {@link PlanningViews} (FR-38, FR-39, FR-338,
 * FR-342, ADR-052, ADR-109, placement P5).
 *
 * <p>Built in M5.
 */
public final class PlanFiguresService implements PlanFigures {

    private final PlanningViews views;

    /**
     * Creates the service.
     *
     * @param views Planning's read side
     */
    public PlanFiguresService(PlanningViews views) {
        this.views = views;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M5.
     */
    @Override
    public PlannedFiguresFact figuresOf(PlanId plan) {
        throw new UnsupportedOperationException("Built in M5");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M5.
     */
    @Override
    public PlanStandingFact standingOf(PlanId plan) {
        throw new UnsupportedOperationException("Built in M5");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M5.
     */
    @Override
    public BaselineInputsFact inputsOf(PlanId plan) {
        throw new UnsupportedOperationException("Built in M5");
    }
}
