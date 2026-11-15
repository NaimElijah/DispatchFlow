package com.dispatchflow.orders.application.port.out;

import java.util.Objects;

/**
 * The geocoding provider couldn't be reached or refused the call (FR-64). It is not a domain
 * refusal: the geocoding job catches it, leaves the request in the outbox and tries it again 15
 * minutes later, for 24 hours (DL §12). Its message never holds the address looked up (NFR-19).
 *
 * <p>Built in M7.
 */
public final class GeocoderUnavailable extends RuntimeException {

    /**
     * Creates the error.
     *
     * @param reason what failed, such as a timeout or the provider's status code; never the address
     */
    public GeocoderUnavailable(String reason) {
        super(Objects.requireNonNull(reason, "reason"));
    }
}
