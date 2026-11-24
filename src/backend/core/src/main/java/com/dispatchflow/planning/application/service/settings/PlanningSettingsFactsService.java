package com.dispatchflow.planning.application.service.settings;

import com.dispatchflow.planning.application.api.PlanningSettingsFacts;
import com.dispatchflow.planning.application.port.out.settings.PlanningSettingsRepository;
import java.time.Duration;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planning's implementation of {@link PlanningSettingsFacts}: it reads the current settings and
 * translates the departure allowance into a {@link Duration}, so no domain type crosses to Live
 * Operations (DL §2.3 rule 1, FR-67, FR-101).
 *
 * <p>To Be Implemented.
 */
@Transactional(readOnly = true)
public class PlanningSettingsFactsService implements PlanningSettingsFacts {

    private final PlanningSettingsRepository settings;

    /**
     * Creates the service.
     *
     * @param settings where the settings are kept
     */
    public PlanningSettingsFactsService(PlanningSettingsRepository settings) {
        this.settings = settings;
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public Duration departureAllowance() {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
