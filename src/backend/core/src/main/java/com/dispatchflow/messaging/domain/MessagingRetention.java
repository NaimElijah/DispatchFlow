package com.dispatchflow.messaging.domain;

import com.dispatchflow.shared.IsraelDateTime;
import java.time.Instant;
import java.time.Period;

/**
 * Messaging's retention periods, which Messaging owns (ADR-108). Messages to recipients and their
 * replies, comments included, are kept 90 days from sending or receipt (NFR-184, NFR-193). An
 * opt-out is kept until the company leaves, whatever retention removes (NFR-202), so it has no
 * period here.
 */
public final class MessagingRetention {

    /** How long messages and replies are kept (NFR-184). */
    public static final Period MESSAGES_AND_REPLIES = Period.ofDays(90);

    private MessagingRetention() {}

    /**
     * When the retention of a message or reply ends: 90 days later at the same Israel time, so a
     * clock change between doesn't move it an hour.
     *
     * @param from when the message was sent or the reply received
     * @return the end of its retention
     */
    public static Instant endOf(Instant from) {
        return from.atZone(IsraelDateTime.ZONE).plus(MESSAGES_AND_REPLIES).toInstant();
    }
}
