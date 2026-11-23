package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.application.port.out.settings.PlanningSettingsRepository;
import com.dispatchflow.planning.domain.settings.PlanningSettings;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the company's one {@link PlanningSettings} in {@code planning.planning_settings}, under
 * {@link PlanningSettings#KEY}.
 */
@Component
@Transactional
class JpaPlanningSettingsRepository implements PlanningSettingsRepository {

    private final AllPlanningSettings settings;
    private final VersionedSave versionedSave;

    /**
     * Creates the adapter.
     *
     * @param settings the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     */
    JpaPlanningSettingsRepository(AllPlanningSettings settings, VersionedSave versionedSave) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
    }

    @Override
    public PlanningSettings current() {
        return settings.findById(PlanningSettings.KEY).orElseGet(PlanningSettings::startingValues);
    }

    /**
     * {@inheritDoc}
     *
     * <p>It goes through the shared save, which stores settings never saved at version 0, and turns
     * a first save that loses the race for the one key into {@code StaleVersion}.
     */
    @Override
    public void save(PlanningSettings changed) {
        versionedSave.save(changed, settings::save);
    }
}
