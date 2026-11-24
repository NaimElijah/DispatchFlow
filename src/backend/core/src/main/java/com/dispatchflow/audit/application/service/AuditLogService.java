package com.dispatchflow.audit.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.AuditedRecord;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.audit.application.port.out.AuditRecords;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit's implementation of {@link AuditLog}: it translates the call into Audit's own {@code
 * AuditRecord} and appends it (DL §2.3 rule 1, NFR-93, ADR-110).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class AuditLogService implements AuditLog {

    private final AuditRecords auditRecords;

    /**
     * Creates the service.
     *
     * @param auditRecords where audit records are appended
     */
    public AuditLogService(AuditRecords auditRecords) {
        this.auditRecords = auditRecords;
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public void succeeded(CallContext call, Instant at, List<AuditedRecord> records) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * {@inheritDoc}
     *
     * <p>To Be Implemented.
     */
    @Override
    public void refused(CallContext call, Instant at, List<AuditedRecord> records, String errorCode) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
