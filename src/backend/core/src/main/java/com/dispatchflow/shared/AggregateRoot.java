package com.dispatchflow.shared;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.AfterDomainEventPublication;
import org.springframework.data.domain.DomainEvents;

/**
 * The base of every aggregate root (DL §3.1, §3.6): it holds the {@link Version} the aggregate was
 * loaded at and the events its methods raised since. It holds no key: each root maps its own
 * private {@code UUID} key (ADR-126): the value of its typed ID, another aggregate's ID it is keyed
 * by, a constant for a singleton, or, for a root found by its date, a UUID beside a date its table
 * keeps unique (ADR-127 decision 5). Only Hibernate rebuilds a stored aggregate, and each root's
 * {@code @PostLoad} check refuses a damaged one.
 *
 * <p>It is mapped for JPA (ADR-126): every root inherits its {@code @Version}, which Hibernate
 * checks on every update. A never-saved aggregate has no version. Its first save stores 0, and
 * every save after moves the version by exactly 1, so every event a change raises carries the
 * version that change is saved at. Spring Data publishes the recorded events when the port's
 * {@code save} runs, in the same transaction, and then forgets them. An instance is saved once per
 * transaction, at the end of its change, with no flush between two of its changes; the shared
 * {@code VersionedSave} fails loudly otherwise. A save that only keeps
 * bookkeeping no business rule reads, and raises no event, may leave the version where it is, as a
 * session's contact times do (DL §4.1).
 */
@MappedSuperclass
public abstract class AggregateRoot {

    /** The version the first save stores (ADR-126). */
    private static final Version FIRST_SAVE = Version.of(0);

    // A Long, not the kernel's Version: Jakarta Persistence applies no converter to a version
    // attribute. Hibernate seeds a null version with 0 when it inserts the aggregate. Spring Data
    // tells a new aggregate by reading this attribute by name, and for a field named "version" it
    // would call version(), which throws before the first save; so the field has another name.
    @jakarta.persistence.Version
    @Column(name = "version")
    private @Nullable Long storedVersion;

    @Transient
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    /**
     * Starts an aggregate that has never been saved, so it has no version yet. A factory calls it
     * for a creation, and Hibernate for a row it loads, before it sets the stored version.
     */
    protected AggregateRoot() {}

    /**
     * The version this instance was loaded at, which the save checks and a view sends as its
     * {@code ETag}.
     *
     * @return the loaded version; raising events doesn't move it
     * @throws AssertionError when the aggregate was never saved, so it has no version: a bug in the
     *     caller, which reads the version of a creation only after its save
     */
    public final Version version() {
        if (storedVersion == null) {
            throw new AssertionError("an aggregate has no version before its first save");
        }
        return Version.of(storedVersion);
    }

    /**
     * The version this instance was stored at, or none: what a use case compares with the caller's
     * {@code If-Match}, or with {@code If-None-Match: *}, when what it loaded may never have been
     * saved, such as a singleton's starting values (ADR-126).
     *
     * @return the stored version, or empty when the aggregate was never saved
     */
    public final Optional<Version> savedVersion() {
        return storedVersion == null ? Optional.empty() : Optional.of(Version.of(storedVersion));
    }

    /**
     * Records an event, to be published when this change is saved. Call it only after every rule
     * of the method has been checked, so a refused call records nothing (DL §3.10).
     *
     * <p>The event's ID carries the version this change is saved at: 0 for an aggregate never
     * saved, and the loaded version plus 1 otherwise. It also carries its position among this
     * change's events, from 0. A call that throws records nothing, so a later call on the same
     * instance still numbers from where the recorded events end.
     *
     * @param type the event's own record type, never a supertype
     * @param aggregateId this aggregate's UUID
     * @param build builds the event around the ID it is given
     * @param <E> the event type
     * @return the event recorded
     * @throws AssertionError when the built event is not exactly of {@code type}, or does not carry
     *     the ID it was given: a bug in the raising method, not a refusal
     */
    protected final <E extends DomainEvent> E raise(Class<E> type, UUID aggregateId, Function<EventId, E> build) {
        Version savedAt =
                storedVersion == null ? FIRST_SAVE : Version.of(storedVersion).next();
        EventId id = new EventId(type.getSimpleName(), aggregateId, savedAt, pendingEvents.size());
        E event = Objects.requireNonNull(build.apply(id), "event");
        // The ID names the type given, so a supertype such as a sealed event interface is refused.
        if (event.getClass() != type) {
            throw new AssertionError("an event must be raised under its own record type");
        }
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
     * The events Spring Data publishes when a repository's {@code save} runs (ADR-126). Spring Data
     * finds and calls it by its annotation, so it needs no wider access.
     *
     * @return an unmodifiable copy, in the order raised
     */
    @DomainEvents
    final List<DomainEvent> domainEvents() {
        return List.copyOf(pendingEvents);
    }

    /** Forgets the recorded events once Spring Data has published them (ADR-126). */
    @AfterDomainEventPublication
    final void clearEvents() {
        pendingEvents.clear();
    }
}
