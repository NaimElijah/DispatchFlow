package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import java.time.Instant;
import java.util.Objects;

/**
 * A recipient's SMS reply as the provider delivered it, a record in {@link ReplyLog} (DL §3.2,
 * FR-357). It holds a phone and free text, so it prints redacted.
 *
 * <p>Built in M12.
 *
 * @param providerId the provider's identifier for the inbound message, which makes a repeated call
 *     take effect once (NFR-199)
 * @param from the phone it came from
 * @param text the reply's text, as delivered
 * @param receivedAt when it was received
 */
public record InboundReply(String providerId, PhoneNumber from, String text, Instant receivedAt)
        implements PersonalData {

    /** Checks every part is present. */
    public InboundReply {
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(receivedAt, "receivedAt");
    }

    /**
     * A form that hides the phone and the text.
     *
     * @return {@code InboundReply[redacted]}
     */
    @Override
    public String toString() {
        return "InboundReply[redacted]";
    }
}
