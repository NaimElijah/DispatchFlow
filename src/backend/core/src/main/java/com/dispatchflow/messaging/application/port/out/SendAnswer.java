package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.messaging.domain.message.ProviderReference;
import java.util.Objects;

/**
 * What the SMS provider answered a send (DL §9.5): accepted with its reference, refused, or unknown
 * after a timeout or a broken call (NFR-198). The use case passes it to {@code StopMessages.accepted},
 * {@code refused} or {@code outcomeUnknown}.
 *
 * <p>Built in M12.
 */
public sealed interface SendAnswer {

    /**
     * The provider accepted the message.
     *
     * <p>Built in M12.
     *
     * @param reference the provider's identifier for it
     */
    record Accepted(ProviderReference reference) implements SendAnswer {

        /** Checks the reference is present. */
        public Accepted {
            Objects.requireNonNull(reference, "reference");
        }
    }

    /**
     * The provider refused the message.
     *
     * <p>Built in M12.
     */
    record Refused() implements SendAnswer {}

    /**
     * The call timed out or broke, so whether the message left is unknown (NFR-198).
     *
     * <p>Built in M12.
     */
    record Unknown() implements SendAnswer {}
}
