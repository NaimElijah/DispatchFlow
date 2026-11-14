package com.dispatchflow.orders.domain;

/**
 * A doubt about a customer's location, which the dispatcher sees before a solve (FR-63, DL §17). A
 * flag never stops the solve (FR-216), and a hand-placed location clears every flag (FR-217).
 */
public enum AddressFlag {
    /** The geocoder found no match (FR-63). */
    NO_MATCH,
    /** The match is below street level (FR-63). */
    PARTIAL_MATCH,
    /** The geocoder found several candidates (FR-63). */
    AMBIGUOUS_MATCH,
    /** The geocoded point lies outside the service area (FR-63, FR-320). */
    OUTSIDE_SERVICE_AREA,
    /** The geocoded point is more than 300 m, a starting value, from the nearest road (FR-63). */
    FAR_FROM_ROAD,
    /** An import created the customer, so its location is to be checked by hand (FR-311). */
    CREATED_BY_IMPORT
}
