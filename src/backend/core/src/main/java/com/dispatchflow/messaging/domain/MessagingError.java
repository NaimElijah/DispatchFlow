package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.DomainError;

/**
 * The refusals of the Recipient Messaging context (DL §4.6). Their fields become the response's
 * parameters (ADR-043), so none carries a phone, a message's text, a reply or a comment: an ID or
 * a limit says what was refused.
 *
 * <p>Most of Messaging's rules refuse nothing: a message that may not go is simply not created, and
 * a reply that counts for nothing is discarded (DL §4.6).
 */
public sealed interface MessagingError extends DomainError {

    /**
     * A rating outside 1 to 10 (FR-376).
     *
     * @param rating the refused number
     */
    record RatingOutOfRange(int rating) implements MessagingError {}

    /** A comment with nothing visible left once it is normalized (FR-360, NFR-203). */
    record BlankComment() implements MessagingError {}

    /**
     * A dispatch phone whose national form, as messages write it, is longer than 11 characters
     * (FR-382, ADR-108).
     *
     * @param maxLength the most characters the national form may have
     */
    record DispatchPhoneTooLong(int maxLength) implements MessagingError {}

    /**
     * A dispatch phone that is not an Israeli number: written in national form, it could not be
     * dialled from Israel (FR-382, ADR-108).
     */
    record DispatchPhoneNotIsraeli() implements MessagingError {}

    /**
     * A company name that can't start a message: blank, or longer than 19 characters (FR-349,
     * FR-382). Access already refuses both, so this means the name reached Messaging from somewhere
     * else.
     *
     * @param maxLength the most characters the name may have
     */
    record UnusableCompanyName(int maxLength) implements MessagingError {}

    /**
     * The sender named a message this stop doesn't have.
     *
     * @param message the unknown message
     */
    record UnknownMessage(OutgoingMessageId message) implements MessagingError {}

    /**
     * The sender claimed a message that is neither waiting to be sent nor retryable after an
     * unknown outcome (DL §9.5).
     *
     * @param message the message
     */
    record MessageNotClaimable(OutgoingMessageId message) implements MessagingError {}

    /**
     * The sender reported a provider's answer for a message it has not claimed, or that was dropped
     * meanwhile (DL §9.5, NFR-190).
     *
     * @param message the message
     */
    record MessageNotSending(OutgoingMessageId message) implements MessagingError {}

    /**
     * A rating request's phone and comments removed before 90 days have passed since its last reply
     * (NFR-184, NFR-193). Other modules declare the same error with the same meaning.
     */
    record RetentionNotOver() implements MessagingError {}
}
