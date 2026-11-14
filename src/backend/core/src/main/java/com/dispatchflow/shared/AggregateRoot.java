package com.dispatchflow.shared;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * The base of every aggregate root (DL §3.1, §3.6): it holds the {@link Version} the aggregate was
 * loaded at and the events its methods raised since. It needs no ID type, because aggregates are
 * keyed differently: most by a typed ID, some by another ID or a date, and singletons by nothing.
 *
 * <p>An instance is saved once: the repository saves it at {@code version().next()} and publishes
 * {@link #pullEvents()} in the same transaction. To change it again, reload it. The one save that
 * doesn't move the version is {@code Session.recordContact}, which raises no event (DL §4.1).
 */
public abstract class AggregateRoot {

    private final Version version;
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

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
     * Records an event, to be published when this change is saved. Call it only after every rule
     * of the method has been checked, so a refused call records nothing (DL §3.10).
     *
     * <p>The event's ID carries {@code version().next()}, the version this change is saved at, and
     * its position among this change's events, from 0. A call that throws records nothing, so a
     * later call on the same instance still numbers from where the recorded events end.
     *
     * @param type the event's record type
     * @param aggregateId this aggregate's UUID
     * @param build builds the event around the ID it is given
     * @param <E> the event type
     * @return the event recorded
     * @throws AssertionError when the built event does not carry the ID it was given: a bug in the
     *     raising method, not a refusal
     */
    protected final <E extends DomainEvent> E raise(Class<E> type, UUID aggregateId, Function<EventId, E> build) {
        EventId id = new EventId(type.getSimpleName(), aggregateId, version.next(), pendingEvents.size());
        E event = Objects.requireNonNull(build.apply(id), "event");
        if (!event.eventId().equals(id)) {
            throw new AssertionError("a raised event must carry the EventId it was built with");
        }
        pendingEvents.add(event);
        return event;
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
