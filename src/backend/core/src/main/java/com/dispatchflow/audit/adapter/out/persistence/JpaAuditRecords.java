package com.dispatchflow.audit.adapter.out.persistence;

import com.dispatchflow.audit.application.port.out.AuditRecords;
import com.dispatchflow.audit.domain.AuditRecord;
import com.dispatchflow.shared.adapter.out.persistence.OpenTransaction;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Keeps audit records in {@code audit.audit_record}, and the records each names in {@code
 * audit.audit_record_named_record}, appended only.
 *
 * <p>It persists each new row through the entity manager: a Spring Data {@code save} would {@code
 * merge} a row whose key is already set, which reads before it writes. The row is inserted when the
 * transaction flushes, and a read-only one never flushes, so a record written in one would be lost
 * with no error: {@link #append} refuses to run outside a read-write transaction (ADR-149).
 */
@Component
class JpaAuditRecords implements AuditRecords {

    private final EntityManager entityManager;

    /**
     * Creates the adapter.
     *
     * @param entityManager the transaction-bound entity manager, which inserts each new record
     */
    JpaAuditRecords(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager");
    }

    /**
     * {@inheritDoc}
     *
     * @throws org.springframework.transaction.IllegalTransactionStateException when no read-write
     *     transaction is open, where the record would never be written
     */
    @Override
    public void append(AuditRecord record) {
        OpenTransaction.require("JpaAuditRecords.append");
        entityManager.persist(new StoredAuditRecord(UUID.randomUUID(), record));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built with the nightly retention run that calls it (ADR-121 decision 9, ADR-149 decision
     * 7). The function it will call, {@code audit.purge_expired_records()}, is migration {@code
     * V12}'s.
     */
    @Override
    public int purgeExpired() {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
