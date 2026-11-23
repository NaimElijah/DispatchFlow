package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.settings.PlanningSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** The Spring Data interface behind {@link JpaPlanningSettingsRepository}. */
interface AllPlanningSettings extends Repository<PlanningSettings, UUID> {

    /**
     * Saves the settings.
     *
     * @param settings the settings
     * @return the managed settings
     */
    PlanningSettings save(PlanningSettings settings);

    /**
     * Loads the settings.
     *
     * @param key {@link PlanningSettings#KEY}
     * @return the settings, or empty before they were first stored
     */
    Optional<PlanningSettings> findById(UUID key);
}
