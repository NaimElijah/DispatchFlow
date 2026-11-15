package com.dispatchflow.messaging.domain;

import java.util.Objects;

/**
 * What a message trigger or a reply is judged by and written with: the messaging settings at that
 * moment ({@link com.dispatchflow.messaging.domain.settings.MessagingSettings#conditions}), the
 * company's name and language from Access, and the phone's standing from its opt-out history (DL
 * §4.6).
 *
 * @param messages whether messages are on (FR-350)
 * @param ratingQuestion whether the delivered message asks for a rating (FR-375)
 * @param dispatchPhone the company's dispatch phone (FR-354, FR-355)
 * @param company the company's name (FR-349)
 * @param language the company's default language (FR-349)
 * @param consent whether the phone may get messages (FR-363)
 */
public record SendingConditions(
        Switch messages,
        Switch ratingQuestion,
        DispatchPhone dispatchPhone,
        CompanyName company,
        MessageLanguage language,
        PhoneConsent consent) {

    /** Checks every part is present. */
    public SendingConditions {
        Objects.requireNonNull(messages, "messages");
        Objects.requireNonNull(ratingQuestion, "ratingQuestion");
        Objects.requireNonNull(dispatchPhone, "dispatchPhone");
        Objects.requireNonNull(company, "company");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(consent, "consent");
    }

    /**
     * Whether a message triggered now may go to the phone: messages are on and the phone has not
     * opted out (FR-350, FR-363).
     *
     * @return {@code true} when both hold
     */
    public boolean mayMessage() {
        return messages == Switch.ON && consent == PhoneConsent.MAY_BE_MESSAGED;
    }

    /**
     * Writes a message under these conditions.
     *
     * @param wording the message's fixed text and its facts
     * @return the text, starting with the company's name, in the company's language
     */
    public MessageText write(Wording wording) {
        return MessageText.of(company, language, wording);
    }
}
