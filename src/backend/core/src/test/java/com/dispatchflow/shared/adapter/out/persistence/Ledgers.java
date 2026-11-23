package com.dispatchflow.shared.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

/**
 * The test-only {@link Ledger}'s Spring Data interface, shaped as ADR-126 asks of an adapter's:
 * package-private, a plain {@link Repository} that declares {@code save} and the one lookup it
 * needs, which loads the aggregate whole.
 */
interface Ledgers extends Repository<Ledger, UUID> {

    /**
     * Saves the ledger and publishes its pending events.
     *
     * @param ledger the ledger
     * @return the managed ledger
     */
    Ledger save(Ledger ledger);

    /**
     * Loads a ledger with its entries.
     *
     * @param id the ledger's key
     * @return the ledger, or empty
     */
    @EntityGraph(attributePaths = "entries")
    Optional<Ledger> findById(UUID id);
}
