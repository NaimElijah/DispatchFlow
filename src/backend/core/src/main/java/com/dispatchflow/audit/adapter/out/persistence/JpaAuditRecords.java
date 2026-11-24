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
     * <p>Built with ADR-041's roles and the {@code migrate} step: the database function it calls
     * runs with its owner's rights, and that owner is a role the database doesn't have until then
     * (ADR-127 decision 8).
     */
    @Override
    public int purgeExpired() {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
