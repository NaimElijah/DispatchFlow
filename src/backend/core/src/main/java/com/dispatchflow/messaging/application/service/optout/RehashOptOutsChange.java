package com.dispatchflow.messaging.application.service.optout;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.application.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of one phone in {@link RehashOptOuts}: it reads the phone's history under every
 * key version still in use, and appends its latest entry again under the current key when none is
 * there yet, with the audit record (ADR-069, NFR-202, ADR-129 decision 3).
 *
 * <p>To Be Implemented. Rows: NFR-202.
 */
@Transactional
public class RehashOptOutsChange {

    private final OptOutList optOuts;
    private final PhoneHasher phoneHasher;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the step's transaction.
     *
     * @param optOuts the phones' opt-out history
     * @param phoneHasher the keyed phone hash, under every key version still in use
     * @param clock the server's time, for the audit record
     * @param auditLog the audit trail
     */
    public RehashOptOutsChange(OptOutList optOuts, PhoneHasher phoneHasher, Clock clock, AuditLog auditLog) {
        this.optOuts = optOuts;
        this.phoneHasher = phoneHasher;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Re-hashes one phone's opt-out under the current key. A phone with an entry under the current
     * key, or with no entry at all, is left alone.
     *
     * <p>To Be Implemented.
     *
     * @param phone a phone still stored
     * @param call the step's server context
     * @return whether it appended an entry under the current key
     */
    public boolean apply(PhoneNumber phone, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
