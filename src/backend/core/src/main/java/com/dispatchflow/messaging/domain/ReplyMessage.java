package com.dispatchflow.messaging.domain;

/**
 * The messages that answer a recipient's reply rather than a stop's progress (FR-358, FR-359,
 * FR-363). The domain says when one is due; how it is sent is left to the application layer
 * (ADR-108).
 */
public enum ReplyMessage {
    /** A rating's one thank-you (FR-358). */
    THANK_YOU,
    /** The one reminder of the rating format, for the first reply that is not a rating (FR-359). */
    FORMAT_REMINDER,
    /** The one confirmation of an opt-out, for a phone messaged in the last 48 hours (FR-363). */
    OPT_OUT_CONFIRMATION
}
