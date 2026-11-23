package com.dispatchflow.messaging.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.application.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher removes a phone's opt-out at the person's own request by phone, by appending an
 * opt-in, noting who did it and when; the latest entry wins (FR-380, NFR-202, ADR-108). {@code
 * OptOutStatus.removeByDispatcher} decides the entry, and one transaction appends it with the audit
 * record. A phone with no opt-out standing gets no entry. Like every changing call it writes its
 * audit record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M12. Rows: FR-380, NFR-202.
 */
@Transactional
public class RemoveOptOut {

    private final PhoneHasher phoneHasher;
    private final OptOutList optOuts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param phoneHasher the keyed phone hash
     * @param optOuts the phones' opt-out history
     * @param clock the server's time
     * @param auditLog the audit trail
     */
    public RemoveOptOut(PhoneHasher phoneHasher, OptOutList optOuts, Clock clock, AuditLog auditLog) {
        this.phoneHasher = phoneHasher;
        this.optOuts = optOuts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Removes the opt-out.
     *
     * <p>Built in M12.
     *
     * @param actor the dispatcher
     * @param phone the phone, as shown on the customer or the order
     */
    public void remove(Actor actor, PhoneNumber phone) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
