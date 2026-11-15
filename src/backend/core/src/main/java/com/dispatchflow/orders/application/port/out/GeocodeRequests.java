package com.dispatchflow.orders.application.port.out;

import com.dispatchflow.orders.domain.NormalizedAddress;
import com.dispatchflow.shared.CustomerId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * The geocoding outbox (FR-12, FR-64, DL §6.1 point 2, DL §12): a use case that registers a
 * customer or changes its address writes a request in its own transaction, and the geocoding job
 * calls the {@link Geocoder} and {@link RoadSnap} for each request outside any transaction. So a
 * provider that is down never stops a customer being saved (FR-64).
 *
 * <p>Built in M7.
 */
public interface GeocodeRequests {

    /** The most requests the geocoding job claims on one Israel day (NFR-135, ADR-085). */
    int DAILY_LOOKUPS = 1_000;

    /**
     * Writes a request, in the caller's transaction, so it commits with the customer it is for.
     *
     * <p>Built in M7.
     *
     * @param customer the customer to geocode
     * @param address its normalized address
     * @param at when the request is made
     */
    void request(CustomerId customer, NormalizedAddress address, Instant at);

    /**
     * Claims the requests whose next try is due: a new request at once, and one tried before 15
     * minutes after its last try (FR-64). Each claimed request is marked tried at {@code now}, so a
     * request the job doesn't finish comes due again.
     *
     * <p>Built in M7.
     *
     * @param now the time of the job
     * @param max the most requests to claim, which keeps the day's lookups within {@value
     *     #DAILY_LOOKUPS} (NFR-135)
     * @return the claimed requests, oldest first, at most {@code max}
     */
    List<GeocodeRequest> due(Instant now, int max);

    /**
     * Removes an answered request, or one given up on after 24 hours (FR-64).
     *
     * <p>Built in M7.
     *
     * @param request the request
     */
    void done(GeocodeRequest request);

    /**
     * How many tries the job made on one Israel day, which {@value #DAILY_LOOKUPS} caps (NFR-135).
     *
     * <p>Built in M7.
     *
     * @param day the day, in {@code Asia/Jerusalem}
     * @return the number of requests claimed that day, 0 or more
     */
    int triedOn(LocalDate day);
}
