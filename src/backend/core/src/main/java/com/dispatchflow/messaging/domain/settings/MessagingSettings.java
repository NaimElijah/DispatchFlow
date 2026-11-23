package com.dispatchflow.messaging.domain.settings;

import com.dispatchflow.messaging.domain.CompanyName;
import com.dispatchflow.messaging.domain.DispatchPhone;
import com.dispatchflow.messaging.domain.MessageLanguage;
import com.dispatchflow.messaging.domain.PhoneConsent;
import com.dispatchflow.messaging.domain.SendingConditions;
import com.dispatchflow.messaging.domain.Switch;
import com.dispatchflow.shared.AggregateRoot;
import java.util.Objects;

/**
 * The company's messaging settings, which Admins change (FR-350, FR-375, FR-125, DL §4.6): whether
 * messages to recipients go, whether the delivered message asks for a rating, and the dispatch
 * phone the messages give. There is one, with no ID. No change raises an event: each is audited by
 * its use case (FR-186), and a change applies to the messages triggered after it (FR-350), because
 * each trigger reads the settings when it runs.
 */
public final class MessagingSettings extends AggregateRoot {

    private Switch messages;
    private Switch ratingQuestion;
    private DispatchPhone dispatchPhone;

    private MessagingSettings(MessagingSettingsSnapshot state) {
        super(state.version());
        this.messages = state.messages();
        this.ratingQuestion = state.ratingQuestion();
        this.dispatchPhone = state.dispatchPhone();
    }

    /**
     * Creates the settings when the system is deployed: messages and the rating question both off
     * (FR-350, FR-375), with the dispatch phone the deployment is given, because two of the
     * messages can't be written without it (FR-354, FR-355; ADR-108).
     *
     * @param dispatchPhone the company's dispatch phone
     * @return the new settings, never saved
     */
    public static MessagingSettings create(DispatchPhone dispatchPhone) {
        return new MessagingSettings(new MessagingSettingsSnapshot(
                null, Switch.OFF, Switch.OFF, Objects.requireNonNull(dispatchPhone, "dispatchPhone")));
    }

    /**
     * Rebuilds the stored settings; only the persistence adapter calls it (DL §3.5). Each value
     * checked itself when the adapter rebuilt it, and no rule ties them together.
     *
     * @param stored the stored state
     * @return the settings at the stored version
     */
    public static MessagingSettings restore(MessagingSettingsSnapshot stored) {
        return new MessagingSettings(stored);
    }

    /**
     * An Admin turns messages to recipients on or off (FR-350). Replies are read either way.
     *
     * @param to the new position
     */
    public void changeMessages(Switch to) {
        messages = Objects.requireNonNull(to, "to");
    }

    /**
     * An Admin turns the rating question on or off (FR-375).
     *
     * @param to the new position
     */
    public void changeRatingQuestion(Switch to) {
        ratingQuestion = Objects.requireNonNull(to, "to");
    }

    /**
     * An Admin changes the dispatch phone (FR-125).
     *
     * @param newPhone the new phone, at most 11 characters in national form (FR-382)
     */
    public void changeDispatchPhone(DispatchPhone newPhone) {
        dispatchPhone = Objects.requireNonNull(newPhone, "newPhone");
    }

    /**
     * The conditions a trigger or a reply is judged by now (DL §4.6).
     *
     * @param company the company's name, from Access
     * @param language the company's default language, from Access
     * @param consent the phone's standing by its opt-out history
     * @return the conditions
     */
    public SendingConditions conditions(CompanyName company, MessageLanguage language, PhoneConsent consent) {
        return new SendingConditions(messages, ratingQuestion, dispatchPhone, company, language, consent);
    }

    /**
     * The settings as stored.
     *
     * @return a snapshot that {@link #restore} rebuilds
     */
    public MessagingSettingsSnapshot snapshot() {
        return new MessagingSettingsSnapshot(savedVersion().orElse(null), messages, ratingQuestion, dispatchPhone);
    }
}
