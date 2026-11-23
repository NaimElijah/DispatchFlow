package com.dispatchflow.planning.application.port.out.settings;

import com.dispatchflow.planning.domain.settings.PlanningSettings;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Where the company's one set of planning settings is kept (DL §3.6, §4.4, FR-183). The persistence
 * adapter loads it whole, which its {@code @PostLoad} check checks (ADR-126), and answers the
 * starting values until an Admin first saves a change (ADR-101).
 */
public interface PlanningSettingsRepository {

    /**
     * The settings as they are now, which every solve, evaluation and default reads (FR-19, FR-67,
     * FR-321).
     *
     * @return the stored settings, or {@code PlanningSettings.startingValues()} when none are stored
     */
    PlanningSettings current();

    /**
     * Saves changed settings with their version check, in the caller's transaction (BR-76). Settings
     * never saved are stored for the first time, at version 0.
     *
     * @param settings the settings, at the version they were loaded at
     * @throws StaleVersion when the settings changed since they were loaded
     */
    void save(PlanningSettings settings);
}
