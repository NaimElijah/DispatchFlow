package com.dispatchflow.messaging.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * Names one message to a recipient (DL §4.6). It is local to Messaging: no other context names a
 * message. The sender gives it to the SMS provider as DispatchFlow's reference, so a provider that
 * drops repeats by reference sends a retried message once (NFR-198).
 *
 * @param value the UUID, which the use case takes from {@code IdGenerator}
 */
public record OutgoingMessageId(UUID value) {

    /** Checks the UUID is present. */
    public OutgoingMessageId {
        Objects.requireNonNull(value, "value");
    }

    /**
     * Wraps a UUID.
     *
     * @param value the UUID
     * @return the ID
     */
    public static OutgoingMessageId of(UUID value) {
        return new OutgoingMessageId(value);
    }
}
