package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.Weight;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** A child entity of the test-only {@link Ledger}: it has its own ID, and no version of its own. */
@Entity
@Table(schema = "persistence_fixture", name = "ledger_entry")
class LedgerEntry {

    @Id
    private UUID id;

    private Weight amount;

    protected LedgerEntry() {}

    LedgerEntry(UUID id, Weight amount) {
        this.id = id;
        this.amount = amount;
    }

    UUID id() {
        return id;
    }

    Weight amount() {
        return amount;
    }

    void reweigh(Weight newAmount) {
        amount = newAmount;
    }
}
