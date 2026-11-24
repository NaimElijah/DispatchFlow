package com.dispatchflow.messaging.application.service.optout;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.application.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher records a phone's opt-out at the person's own request by phone, noting who did it
 * and when (FR-380, NFR-202). The phone's keyed hash from {@link PhoneHasher} finds its history,
 * {@code OptOutStatus.recordByDispatcher} decides the entry, and one transaction appends it with
 * the audit record. A phone already opted out gets no second entry. Like every changing call it
 * writes its audit record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M12. Rows: FR-380, NFR-202.
 */
@Transactional
public class RecordOptOut {

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
    public RecordOptOut(PhoneHasher phoneHasher, OptOutList optOuts, Clock clock, AuditLog auditLog) {
        this.phoneHasher = phoneHasher;
        this.optOuts = optOuts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Records the opt-out.
     *
     * <p>Built in M12.
     *
     * @param actor the dispatcher
     * @param phone the phone, as shown on the customer or the order
     */
    public void record(Actor actor, PhoneNumber phone) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
