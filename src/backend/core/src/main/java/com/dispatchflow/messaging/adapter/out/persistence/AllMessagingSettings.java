package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.domain.settings.MessagingSettings;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** The Spring Data interface behind {@link JpaMessagingSettingsRepository}. */
interface AllMessagingSettings extends Repository<MessagingSettings, UUID> {

    /**
     * Saves the settings.
     *
     * @param settings the settings
     * @return the managed settings
     */
    MessagingSettings save(MessagingSettings settings);

    /**
     * Loads the settings.
     *
     * @param key {@link MessagingSettings#KEY}
     * @return the settings, or empty before they were first stored
     */
    Optional<MessagingSettings> findById(UUID key);
}
