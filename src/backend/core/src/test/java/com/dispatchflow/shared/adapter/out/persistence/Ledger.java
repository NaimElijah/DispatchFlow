package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.AggregateRoot;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.DomainEvent;
import com.dispatchflow.shared.EventId;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.PlanId;
import com.dispatchflow.shared.Priority;
import com.dispatchflow.shared.StoredStateInvalid;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A test-only aggregate that {@link VersionedSaveIT} saves, mapped as ADR-126 asks a lane to map
 * its own: a private {@code UUID} key, a child entity in an ordered list, an enum stored as text,
 * kernel values in its columns, and a {@code @PostLoad} check. Its tables come from {@code
 * db/fixture}, in the {@code persistence_fixture} schema.
 */
@Entity
@Table(schema = "persistence_fixture", name = "ledger")
class Ledger extends AggregateRoot {

    enum Status {
        OPEN,
        CLOSED
    }

    record LedgerOpened(EventId eventId, Instant occurredAt) implements DomainEvent {}

    record LedgerRenamed(EventId eventId, Instant occurredAt) implements DomainEvent {}

    record EntryAdded(EventId eventId, Instant occurredAt) implements DomainEvent {}

    record EntryReweighed(EventId eventId, Instant occurredAt) implements DomainEvent {}

    @Id
    private UUID id;

    private String name;

    @Enumerated(EnumType.STRING)
    private Status status;

    private DeliveryDate bookedFor;

    @Embedded
    @AttributeOverride(name = "start", column = @Column(name = "window_start"))
    @AttributeOverride(name = "end", column = @Column(name = "window_end"))
    private TimeWindow window;

    @Embedded
    @AttributeOverride(name = "latitude", column = @Column(name = "depot_latitude"))
    @AttributeOverride(name = "longitude", column = @Column(name = "depot_longitude"))
    private @Nullable GeoPoint depot;

    private Priority priority;

    private @Nullable PlanId plan;

    private @Nullable PhoneNumber phone;

    private @Nullable EventId cause;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "ledger_id", nullable = false)
    @OrderColumn(name = "entry_index", nullable = false)
    private List<LedgerEntry> entries = new ArrayList<>();

    protected Ledger() {}

    private Ledger(UUID id, String name, DeliveryDate bookedFor, TimeWindow window, @Nullable GeoPoint depot) {
        this.id = id;
        this.name = name;
        this.status = Status.OPEN;
        this.bookedFor = bookedFor;
        this.window = window;
        this.depot = depot;
        this.priority = Priority.byDefault();
    }

    static Ledger open(
            UUID id, String name, DeliveryDate bookedFor, TimeWindow window, @Nullable GeoPoint depot, Instant now) {
        Ledger ledger = new Ledger(id, name, bookedFor, window, depot);
        ledger.raise(LedgerOpened.class, id, eventId -> new LedgerOpened(eventId, now));
        return ledger;
    }

    UUID id() {
        return id;
    }

    String name() {
        return name;
    }

    Status status() {
        return status;
    }

    DeliveryDate bookedFor() {
        return bookedFor;
    }

    TimeWindow window() {
        return window;
    }

    Optional<GeoPoint> depot() {
        return Optional.ofNullable(depot);
    }

    List<LedgerEntry> entries() {
        return List.copyOf(entries);
    }

    Priority priority() {
        return priority;
    }

    Optional<PlanId> plan() {
        return Optional.ofNullable(plan);
    }

    Optional<PhoneNumber> phone() {
        return Optional.ofNullable(phone);
    }

    Optional<EventId> cause() {
        return Optional.ofNullable(cause);
    }

    /** Sets the kernel values no other method sets, before the first save; it raises nothing. */
    void describe(Priority newPriority, PlanId newPlan, PhoneNumber newPhone, EventId newCause) {
        priority = newPriority;
        plan = newPlan;
        phone = newPhone;
        cause = newCause;
    }

    /** Adds an entry that carries every kernel value an entry can. */
    void addDetailedEntry(
            UUID entryId, Weight amount, Attempt attempt, Volume volume, DaySeconds due, TimeWindow slot, Instant now) {
        entries.add(new LedgerEntry(entryId, amount, attempt, volume, due, slot));
        raise(EntryAdded.class, id, eventId -> new EntryAdded(eventId, now));
    }

    /** Changes the root's own column only. */
    void rename(String newName, Instant now) {
        name = newName;
        raise(LedgerRenamed.class, id, eventId -> new LedgerRenamed(eventId, now));
    }

    /** Changes the root's owned list: Hibernate moves the version itself. */
    void addEntry(UUID entryId, Weight amount, Instant now) {
        entries.add(new LedgerEntry(entryId, amount));
        raise(EntryAdded.class, id, eventId -> new EntryAdded(eventId, now));
    }

    /** Changes a child entity's column only: Hibernate leaves the root's version alone. */
    void reweigh(UUID entryId, Weight amount, Instant now) {
        entries.stream()
                .filter(entry -> entry.id().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no such entry"))
                .reweigh(amount);
        raise(EntryReweighed.class, id, eventId -> new EntryReweighed(eventId, now));
    }

    @PostLoad
    void checkStored() {
        if (name.isBlank()) {
            throw new StoredStateInvalid(Ledger.class, "a ledger has a name");
        }
    }
}
