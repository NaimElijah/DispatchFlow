package com.dispatchflow.access.application.api;

import com.dispatchflow.shared.PersonalData;
import java.util.Objects;

/**
 * Where a push notification reaches one signed-in phone: the push service's token for the device
 * (ADR-026, ADR-083, placement P1). It identifies a person's device and never enters a log, so it
 * prints redacted (NFR-19).
 *
 * <p>To Be Implemented.
 *
 * @param token the push service's device token, as the phone registered it
 */
public record PushAddress(String token) implements PersonalData {

    /** Checks the token is present. */
    public PushAddress {
        Objects.requireNonNull(token, "token");
    }

    /**
     * A form that hides the token.
     *
     * @return {@code PushAddress[redacted]}
     */
    @Override
    public String toString() {
        return "PushAddress[redacted]";
    }
}
