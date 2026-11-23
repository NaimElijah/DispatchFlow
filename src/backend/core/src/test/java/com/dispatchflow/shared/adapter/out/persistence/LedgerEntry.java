package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.TimeWindow;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A child entity of the test-only {@link Ledger}: it has its own ID, and no version of its own. It
 * carries the kernel values the root doesn't, so that every converter and embeddable is stored and
 * read back through Hibernate; its {@code slot} keeps {@code TimeWindow}'s default column names.
 */
@Entity
@Table(schema = "persistence_fixture", name = "ledger_entry")
class LedgerEntry {

    @Id
    private UUID id;

    private Weight amount;

    private Attempt attempt;

    private @Nullable Volume volume;

    @Embedded
    @AttributeOverride(name = "date", column = @Column(name = "due_date"))
    @AttributeOverride(name = "seconds", column = @Column(name = "due_seconds"))
    private @Nullable DaySeconds due;

    @Embedded
    private @Nullable TimeWindow slot;

    protected LedgerEntry() {}

    LedgerEntry(UUID id, Weight amount) {
        this(id, amount, Attempt.FIRST, null, null, null);
    }

    LedgerEntry(
            UUID id,
            Weight amount,
            Attempt attempt,
            @Nullable Volume volume,
            @Nullable DaySeconds due,
            @Nullable TimeWindow slot) {
        this.id = id;
        this.amount = amount;
        this.attempt = attempt;
        this.volume = volume;
        this.due = due;
        this.slot = slot;
    }

    UUID id() {
        return id;
    }

    Weight amount() {
        return amount;
    }

    Attempt attempt() {
        return attempt;
    }

    Optional<Volume> volume() {
        return Optional.ofNullable(volume);
    }

    Optional<DaySeconds> due() {
        return Optional.ofNullable(due);
    }

    Optional<TimeWindow> slot() {
        return Optional.ofNullable(slot);
    }

    void reweigh(Weight newAmount) {
        amount = newAmount;
    }
}
