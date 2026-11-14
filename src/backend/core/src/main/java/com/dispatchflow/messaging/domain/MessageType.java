package com.dispatchflow.messaging.domain;

/**
 * The kinds of message a stop's recipient gets (FR-351 to FR-356, FR-372). With the stop and, for
 * {@link #TIME_CHANGED}, how many came before it, the type identifies a message, which is sent at
 * most once (NFR-191).
 */
public enum MessageType {
    /** The route started, or the stop joined a started route, with the projected window (FR-351, FR-372). */
    OUT_FOR_DELIVERY,
    /** The stop is about 15 minutes away (FR-352). */
    ARRIVING_SOON,
    /** The projected arrival moved 30 minutes or more from the one last sent, with a new window (FR-353). */
    TIME_CHANGED,
    /** The order was dropped, moved to another date or cancelled after the route started (FR-354). */
    DELIVERY_CHANGED,
    /** The stop's first outcome is failed (FR-355). */
    MISSED_YOU,
    /** The stop's first outcome is delivered (FR-356). */
    DELIVERED;

    /** The most {@link #TIME_CHANGED} messages a stop gets (FR-384). */
    private static final int MOST_TIME_CHANGES = 2;

    /**
     * How many messages of this type a stop may get.
     *
     * @return 2 for {@link #TIME_CHANGED} (FR-384), and 1 for every other type (NFR-191)
     */
    public int mostPerStop() {
        return this == TIME_CHANGED ? MOST_TIME_CHANGES : 1;
    }

    /**
     * Whether a message of this type gives the recipient an arrival window.
     *
     * @return {@code true} for {@link #OUT_FOR_DELIVERY} and {@link #TIME_CHANGED}
     */
    public boolean givesAnArrival() {
        return this == OUT_FOR_DELIVERY || this == TIME_CHANGED;
    }
}
