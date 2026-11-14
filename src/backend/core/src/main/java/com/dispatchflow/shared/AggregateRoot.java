package com.dispatchflow.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The base of every aggregate root (DL §3.1, §3.6): it holds the {@link Version} the aggregate was
 * loaded at and the events its methods raised since. It needs no ID type, because aggregates are
 * keyed differently: most by a typed ID, some by another ID or a date, and singletons by nothing.
 *
 * <p>An instance is saved once: the repository saves it at {@code version().next()} and publishes
 * {@link #pullEvents()} in the same transaction. To change it again, reload it.
 */
public abstract class AggregateRoot {

    private final Version version;
    private final List<DomainEvent> pendingEvents = new ArrayList<>();
    private int eventIdsIssued;

    /**
     * Sets the version: {@link Version#NEW} from a creation factory, the stored one from {@code
     * restore}.
     *
     * @param version the version this instance was created or loaded at
     */
    protected AggregateRoot(Version version) {
        this.version = Objects.requireNonNull(version, "version");
    }

    /**
     * The version this instance was created or loaded at, which the save checks.
     *
     * @return the loaded version; raising events doesn't move it
     */
    public final Version version() {
        return version;
    }

    /**
     * Builds the ID of an event this change will raise. Ordinals count up from 0 in the order IDs
     * are built, so two events of one change never share an ID.
     *
     * @param eventType the event's record type
     * @param aggregateId this aggregate's UUID
     * @return an ID carrying {@code version().next()}, the version this change is saved at
     */
    protected final EventId nextEventId(Class<? extends DomainEvent> eventType, UUID aggregateId) {
        return new EventId(eventType.getSimpleName(), aggregateId, version.next(), eventIdsIssued++);
    }

    /**
     * Records an event, to be published when this change is saved. Call it only after every rule
     * of the method has been checked, so a refused call records nothing (DL §3.10).
     *
     * @param event the fact this method's change produced
     */
    protected final void raise(DomainEvent event) {
        pendingEvents.add(Objects.requireNonNull(event, "event"));
    }

    /**
     * The events recorded so far, without removing them; for tests that check a refused call
     * recorded nothing.
     *
     * @return an unmodifiable copy, in the order raised
     */
    public final List<DomainEvent> pendingEvents() {
        return List.copyOf(pendingEvents);
    }

    /**
     * Hands the recorded events to the save, which publishes them, and forgets them.
     *
     * @return an unmodifiable copy, in the order raised
     */
    public final List<DomainEvent> pullEvents() {
        List<DomainEvent> pulled = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return pulled;
    }
}
