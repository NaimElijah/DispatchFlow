package com.dispatchflow.messaging.domain.settings;

import com.dispatchflow.messaging.domain.CompanyName;
import com.dispatchflow.messaging.domain.DispatchPhone;
import com.dispatchflow.messaging.domain.MessageLanguage;
import com.dispatchflow.messaging.domain.PhoneConsent;
import com.dispatchflow.messaging.domain.SendingConditions;
import com.dispatchflow.messaging.domain.Switch;
import com.dispatchflow.shared.AggregateRoot;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;

/**
 * The company's messaging settings, which Admins change (FR-350, FR-375, FR-125, DL §4.6): whether
 * messages to recipients go, whether the delivered message asks for a rating, and the dispatch
 * phone the messages give. There is one, with no ID. No change raises an event: each is audited by
 * its use case (FR-186), and a change applies to the messages triggered after it (FR-350), because
 * each trigger reads the settings when it runs.
 *
 * <p>It is mapped for JPA (ADR-126). Having no ID, it is stored under the constant {@link #KEY}
 * (ADR-127 decision 5).
 */
@Entity
@Table(schema = "messaging", name = "messaging_settings")
public class MessagingSettings extends AggregateRoot {

    /**
     * The one stored row's key. The settings have no ID, but every root keys on a UUID (ADR-126
     * decision 10), so they key on this constant, which the table allows as its only key.
     */
    public static final UUID KEY = UUID.fromString("2d238743-e3ee-4341-a69e-eecd5c010419");

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private Switch messages;

    @Enumerated(EnumType.STRING)
    private Switch ratingQuestion;

    private DispatchPhone dispatchPhone;

    /** For Hibernate, which sets the fields from the stored row and then runs {@link #checkStored}. */
    protected MessagingSettings() {}

    private MessagingSettings(Switch messages, Switch ratingQuestion, DispatchPhone dispatchPhone) {
        this.id = KEY;
        this.messages = messages;
        this.ratingQuestion = ratingQuestion;
        this.dispatchPhone = dispatchPhone;
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
        return new MessagingSettings(Switch.OFF, Switch.OFF, Objects.requireNonNull(dispatchPhone, "dispatchPhone"));
    }

    /**
     * The load check every root has (ADR-126 decision 8). It checks nothing: each value checked
     * itself when its converter rebuilt it, and no rule ties them together.
     */
    @PostLoad
    void checkStored() {
        // Deliberately empty: see the Javadoc.
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
     * Whether messages to recipients go (FR-350).
     *
     * @return on or off
     */
    public Switch messages() {
        return messages;
    }

    /**
     * Whether the delivered message asks for a rating (FR-375).
     *
     * @return on or off
     */
    public Switch ratingQuestion() {
        return ratingQuestion;
    }

    /**
     * The dispatch phone the messages give (FR-125).
     *
     * @return the phone
     */
    public DispatchPhone dispatchPhone() {
        return dispatchPhone;
    }
}
