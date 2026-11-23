package com.dispatchflow.messaging.adapter.out.persistence;

import com.dispatchflow.messaging.domain.OptOutRecord;
import com.dispatchflow.messaging.domain.OptOutRecord.Change;
import com.dispatchflow.messaging.domain.PhoneHash;
import com.dispatchflow.shared.AccountId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.jspecify.annotations.Nullable;

/**
 * One stored {@link OptOutRecord}, a record appended and never changed (DL §3.2, ADR-127 decision
 * 6). The record has no ID, so its row is keyed by a random UUID that nothing outside the table
 * sees.
 *
 * <p>A phone's latest entry is the one appended last, whatever its time (ADR-108), so the row keeps
 * its place in the history in {@code appended}, a number the database draws on insert. Hibernate
 * never writes it, and only queries read it, to order a history.
 */
@Entity
@Immutable
@Table(schema = "messaging", name = "opt_out")
class StoredOptOut {

    @Id
    private UUID id;

    @Column(insertable = false, updatable = false)
    private long appended;

    @Column(name = "phone_hash")
    private PhoneHash phone;

    @Enumerated(EnumType.STRING)
    private Change change;

    private Instant madeAt;

    private @Nullable AccountId dispatcher;

    /** For Hibernate. */
    protected StoredOptOut() {}

    /**
     * Stores an entry.
     *
     * @param id the row's key, which the adapter draws
     * @param entry the entry
     */
    StoredOptOut(UUID id, OptOutRecord entry) {
        this.id = id;
        this.phone = entry.phone();
        this.change = entry.change();
        this.madeAt = entry.at();
        this.dispatcher = entry.dispatcher().orElse(null);
    }

    /**
     * The entry this row stores.
     *
     * @return the entry, rebuilt through {@link OptOutRecord#ofStored}
     * @throws com.dispatchflow.shared.StoredStateInvalid when the row is an opt-in with no
     *     dispatcher
     */
    OptOutRecord toRecord() {
        return OptOutRecord.ofStored(phone, change, madeAt, Optional.ofNullable(dispatcher));
    }
}
