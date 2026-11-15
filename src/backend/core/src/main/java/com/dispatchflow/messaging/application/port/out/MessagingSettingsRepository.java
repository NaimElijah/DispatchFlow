package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.settings.MessagingSettings;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;

/**
 * Where the company's one {@link MessagingSettings} is kept, with no ID (DL §3.6, §4.6, FR-350).
 * Before an Admin first sets them there are none, and messages and the rating question are off
 * (FR-350, FR-375).
 *
 * <p>Built in M12.
 */
public interface MessagingSettingsRepository {

    /**
     * The settings.
     *
     * <p>Built in M12.
     *
     * @return the settings, or empty before an Admin first set them
     */
    Optional<MessagingSettings> get();

    /**
     * Stores the first settings, in the caller's transaction.
     *
     * <p>Built in M12.
     *
     * @param settings the settings, at {@code Version.NEW}
     * @throws StaleVersion when another Admin stored them first
     */
    void add(MessagingSettings settings);

    /**
     * Stores changed settings, checking the version they were loaded at (BR-76).
     *
     * <p>Built in M12.
     *
     * @param settings the settings
     * @throws StaleVersion when another Admin saved them since they were loaded
     */
    void save(MessagingSettings settings);
}
