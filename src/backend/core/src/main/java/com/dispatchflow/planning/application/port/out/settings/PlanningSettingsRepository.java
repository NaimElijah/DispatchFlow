package com.dispatchflow.planning.application.port.out.settings;

import com.dispatchflow.planning.domain.settings.PlanningSettings;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Where the company's one set of planning settings is kept (DL §3.6, §4.4, FR-183). The persistence
 * adapter loads it through {@code PlanningSettings.restore}, and answers the starting values until an
 * Admin first saves a change (ADR-101).
 *
 * <p>Built in M0 and M4, method by method.
 */
public interface PlanningSettingsRepository {

    /**
     * The settings as they are now, which every solve, evaluation and default reads (FR-19, FR-67,
     * FR-321).
     *
     * <p>Built in M0.
     *
     * @return the stored settings, or {@code PlanningSettings.startingValues()} when none are stored
     */
    PlanningSettings current();

    /**
     * Saves changed settings with their version check, in the caller's transaction (BR-76). Settings
     * at {@code Version.NEW} are stored for the first time.
     *
     * <p>Built in M4.
     *
     * @param settings the settings, at the version they were loaded at
     * @throws StaleVersion when the settings changed since they were loaded
     */
    void save(PlanningSettings settings);
}
