package com.dispatchflow.messaging.domain.settings;

import com.dispatchflow.messaging.domain.DispatchPhone;
import com.dispatchflow.messaging.domain.Switch;
import com.dispatchflow.shared.Version;
import java.util.Objects;

/**
 * {@link MessagingSettings}' whole state, which the repository stores and {@link
 * MessagingSettings#restore} rebuilds. The repository rebuilds the phone with {@link
 * DispatchPhone#ofStored}.
 *
 * @param version the version it was loaded at
 * @param messages whether messages to recipients go (FR-350)
 * @param ratingQuestion whether the delivered message asks for a rating (FR-375)
 * @param dispatchPhone the company's dispatch phone (FR-125)
 */
public record MessagingSettingsSnapshot(
        Version version, Switch messages, Switch ratingQuestion, DispatchPhone dispatchPhone) {

    /** Checks every part is present. */
    public MessagingSettingsSnapshot {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(ratingQuestion, "ratingQuestion");
        Objects.requireNonNull(dispatchPhone, "dispatchPhone");
    }
}
