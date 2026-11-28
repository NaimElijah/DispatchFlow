package com.dispatchflow.planning.application.port.out.settings;

import com.dispatchflow.planning.domain.settings.PlanningSettings;
import java.util.Objects;

/**
 * The company's planning settings, in memory, for application tests: the starting values, as the
 * adapter answers before an Admin first saves a change (ADR-101), or the settings a test gives.
 */
public final class InMemoryPlanningSettingsRepository implements PlanningSettingsRepository {

    private final PlanningSettings settings;

    /** Holds the starting values. */
    public InMemoryPlanningSettingsRepository() {
        this(PlanningSettings.startingValues());
    }

    /**
     * Holds the given settings.
     *
     * @param settings what {@link #current()} answers
     */
    public InMemoryPlanningSettingsRepository(PlanningSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    @Override
    public PlanningSettings current() {
        return settings;
    }

    /**
     * Not needed by any application test yet.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public void save(PlanningSettings changed) {
        throw new UnsupportedOperationException("the in-memory settings don't save a change yet");
    }
}
