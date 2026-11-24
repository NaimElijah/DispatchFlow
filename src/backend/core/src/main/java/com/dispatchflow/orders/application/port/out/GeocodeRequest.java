package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.NormalizedAddress;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.PersonalData;
import java.time.Instant;
import java.util.Objects;

/**
 * One customer's address waiting in the geocoding outbox (FR-12, FR-64, DL §12). The customer and
 * the address name it: a request for an address the customer no longer has is still answered,
 * and {@code Customer.recordGeocode} ignores the answer (BR-104). The address is personal data, so
 * it prints redacted.
 *
 * <p>To Be Implemented.
 *
 * @param customer the customer to geocode
 * @param address its normalized address when the request was made
 * @param requestedAt when the request was made, from which its 24 hours of retries count (FR-64)
 */
public record GeocodeRequest(CustomerId customer, NormalizedAddress address, Instant requestedAt)
        implements PersonalData {

    /** Checks every part is present. */
    public GeocodeRequest {
        Objects.requireNonNull(customer, "customer");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }

    /**
     * A form that hides the address.
     *
     * @return {@code GeocodeRequest[redacted]}
     */
    @Override
    public String toString() {
        return "GeocodeRequest[redacted]";
    }
}
