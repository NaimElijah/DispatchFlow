package com.dispatchflow.orders.application.port.out.customer;

import com.dispatchflow.orders.application.port.out.GeocoderUnavailable;
import java.util.Objects;

/**
 * The road network, OSRM, couldn't be reached or refused a snap (FR-63). It is not a domain
 * refusal: the geocoding job catches it as it catches {@link GeocoderUnavailable}, leaves the
 * request in the outbox and tries it again 15 minutes later, for 24 hours (FR-64, DL §12). Its
 * message and its cause never hold the point (NFR-19): the adapter wraps the I/O error without the
 * HTTP client's message, which holds the URL's coordinates.
 *
 * <p>Built in M7.
 */
public final class RoadSnapUnavailable extends RuntimeException {

    /**
     * Creates the error.
     *
     * @param reason what failed, such as a timeout or OSRM's status code; never the point
     */
    public RoadSnapUnavailable(String reason) {
        super(Objects.requireNonNull(reason, "reason"));
    }
}
