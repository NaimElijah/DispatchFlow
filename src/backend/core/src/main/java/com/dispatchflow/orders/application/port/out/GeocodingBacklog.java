package com.dispatchflow.orders.application.port.out;

/**
 * The geocoding lookups still waiting, which the dispatcher is told about beside the flagged
 * addresses (NFR-135, FR-64): how many wait, and whether today's cap held them back. It holds
 * counts only, so it is no personal data.
 *
 * <p>Built in M7.
 *
 * @param waiting how many requests wait in the outbox, 0 or more
 * @param capReached whether today's {@value GeocodeRequests#DAILY_LOOKUPS} lookups are used up, so
 *     the ones waiting wait for the next Israel day (NFR-135)
 */
public record GeocodingBacklog(int waiting, boolean capReached) {}
