package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.DispatchPhone;
import com.dispatchflow.messaging.domain.Switch;
import com.dispatchflow.shared.Version;
import java.util.Objects;
import java.util.Optional;

/**
 * The messaging part of the company settings page, which an Admin reads and changes (FR-125,
 * FR-350, FR-375, FR-382, placement P7). Before the first change messages and the rating question
 * are off, and there is no dispatch phone.
 *
 * <p>To Be Implemented.
 *
 * @param messages whether messages to recipients go (FR-350)
 * @param ratingQuestion whether the delivered message asks for a rating (FR-375)
 * @param dispatchPhone the dispatch phone the messages give, or empty before an Admin set one
 * @param version the settings' version, which a change sends back; empty before the
 *     first change (BR-76)
 */
public record MessagingSettingsView(
        Switch messages, Switch ratingQuestion, Optional<DispatchPhone> dispatchPhone, Optional<Version> version) {

    /** Checks every part is present, possibly empty. */
    public MessagingSettingsView {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(ratingQuestion, "ratingQuestion");
        Objects.requireNonNull(dispatchPhone, "dispatchPhone");
        Objects.requireNonNull(version, "version");
    }
}
