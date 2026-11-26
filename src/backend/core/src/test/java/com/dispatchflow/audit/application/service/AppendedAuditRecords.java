package com.dispatchflow.audit.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dispatchflow.audit.application.port.out.AuditRecords;
import com.dispatchflow.audit.domain.AuditRecord;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Audit's store, in memory: what was appended, in order. The tests of the audit log and of its web
 * callers run the real {@link AuditLogService} over it, so a record Audit would refuse fails there
 * as it would against the database.
 */
public final class AppendedAuditRecords implements AuditRecords {

    private final List<AuditRecord> records = new CopyOnWriteArrayList<>();

    @Override
    public void append(AuditRecord record) {
        records.add(record);
    }

    /**
     * {@inheritDoc}
     *
     * @throws AssertionError always: the audit log never purges
     */
    @Override
    public int purgeExpired() {
        throw new AssertionError("the audit log never purges");
    }

    /**
     * What was appended.
     *
     * @return the records, in the order they were appended
     */
    public List<AuditRecord> records() {
        return List.copyOf(records);
    }

    /**
     * The one record appended.
     *
     * @return it
     * @throws AssertionError when none, or more than one, was appended
     */
    public AuditRecord only() {
        assertThat(records).hasSize(1);
        return records.getFirst();
    }
}
