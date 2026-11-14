package com.dispatchflow.shared;

import java.time.Instant;

/**
 * A fact an aggregate's method raised (DL §7.3): a past-tense record in its module's {@code
 * events} package, carrying only kernel types, the package's own enums, {@code Instant}, numbers,
 * booleans and lists of these. Never free text and never personal data.
 */
public interface DomainEvent {

    /**
     * The event's ID, which {@link AggregateRoot}'s {@code raise} builds.
     *
     * @return the ID, unique per aggregate, version and ordinal
     */
    EventId eventId();

    /**
     * When the fact happened.
     *
     * @return the {@code now} the raising method was given
     */
    Instant occurredAt();
}
