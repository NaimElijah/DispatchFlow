package com.dispatchflow.shared;

import java.util.List;

/**
 * What an in-memory fake's {@code save} does with an aggregate's events, as Spring Data's does
 * (ADR-126): hands them over, then forgets them, so a second save publishes only what was raised
 * since. It reaches the package-private methods Spring Data calls, which a fake in a module's
 * package can't.
 */
public final class SaveEvents {

    private SaveEvents() {}

    /**
     * Publishes an aggregate's pending events and clears them.
     *
     * @param aggregate the aggregate a fake saves
     * @return the events published, in the order raised
     */
    public static List<DomainEvent> publish(AggregateRoot aggregate) {
        List<DomainEvent> published = aggregate.domainEvents();
        aggregate.clearEvents();
        return published;
    }
}
