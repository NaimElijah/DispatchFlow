package com.dispatchflow.liveops.application.api;

import com.dispatchflow.shared.PlanId;
import java.util.List;

/**
 * What happened at a plan's stops, which Reporting reads when a figure is asked for, so a later
 * correction changes the next figure (FR-38, FR-333, ADR-109 reading 8).
 *
 * <p>Built in M5.
 */
public interface StopResults {

    /**
     * Every stop of a plan with its arrival and effective outcome.
     *
     * <p>Built in M5.
     *
     * @param plan the plan
     * @return one result per stop of the plan's current revision, each once; empty for a plan with
     *     no stop
     */
    List<StopResultFact> of(PlanId plan);
}
