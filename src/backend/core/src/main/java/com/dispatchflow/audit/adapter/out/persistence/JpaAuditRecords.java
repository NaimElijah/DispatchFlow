package com.dispatchflow.audit.adapter.out.persistence;

import com.dispatchflow.audit.application.port.out.AuditRecords;
import com.dispatchflow.audit.domain.AuditRecord;
import jakarta.persistence.EntityManager;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Keeps audit records in {@code audit.audit_record}, and the records each names in {@code
 * audit.audit_record_named_record}, appended only.
 *
 * <p>It persists each new row through the entity manager: a Spring Data {@code save} would {@code
 * merge} a row whose key is already set, which reads before it writes.
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

    @Override
    public void append(AuditRecord record) {
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
