package com.dispatchflow.orders.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link ForgetUnusedGeocodes}: after the geocoder's adapter has deleted the
 * unused cache entries, it records the run through {@link AuditLog#succeeded}, naming no record
 * (NFR-93, ADR-110, ADR-129 decision 3).
 *
 * <p>To Be Implemented. It comes with the whole nightly retention run (ADR-121).
 */
@Transactional
public class ForgetUnusedGeocodesChange {

    private final AuditLog auditLog;

    /**
     * Creates the job's transaction.
     *
     * @param auditLog where the run's own audit record is written
     */
    public ForgetUnusedGeocodesChange(AuditLog auditLog) {
        this.auditLog = auditLog;
    }

    /**
     * Records the run.
     *
     * <p>To Be Implemented.
     *
     * @param at the time of the job, which the cutoff counted back from
     * @param call the job's server step
     */
    public void recordRun(Instant at, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
