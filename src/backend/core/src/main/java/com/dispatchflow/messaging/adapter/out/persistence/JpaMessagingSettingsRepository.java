package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.application.port.out.MessagingSettingsRepository;
import com.dispatchflow.messaging.domain.settings.MessagingSettings;
import com.dispatchflow.shared.adapter.out.persistence.VersionedSave;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Keeps the company's one {@link MessagingSettings} in {@code messaging.messaging_settings}, under
 * {@link MessagingSettings#KEY}.
 */
@Component
class JpaMessagingSettingsRepository implements MessagingSettingsRepository {

    private final AllMessagingSettings settings;
    private final VersionedSave versionedSave;

    /**
     * Creates the adapter.
     *
     * @param settings the Spring Data interface
     * @param versionedSave the shared save, which moves the version by exactly 1
     */
    JpaMessagingSettingsRepository(AllMessagingSettings settings, VersionedSave versionedSave) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.versionedSave = Objects.requireNonNull(versionedSave, "versionedSave");
    }

    @Override
    public Optional<MessagingSettings> get() {
        return settings.findById(MessagingSettings.KEY);
    }

    /**
     * {@inheritDoc}
     *
     * <p>It goes through the shared save, which turns a first save that loses the race for the one
     * key into {@code StaleVersion}.
     */
    @Override
    public void add(MessagingSettings newSettings) {
        versionedSave.save(newSettings, settings::save);
    }

    @Override
    public void save(MessagingSettings changed) {
        versionedSave.save(changed, settings::save);
    }
}
