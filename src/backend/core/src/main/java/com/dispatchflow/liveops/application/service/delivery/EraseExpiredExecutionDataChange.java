package com.dispatchflow.liveops.application.service.delivery;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.port.out.DeliveryRepository;
import com.dispatchflow.liveops.application.port.out.PositionLog;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link EraseExpiredExecutionData} (NFR-31, NFR-184, NFR-260, ADR-075): each
 * call is one transaction of its own, so one stale delivery doesn't hold back the rest. Each step
 * that changes something writes its audit record, with the count and no personal data (ADR-110).
 *
 * <p>Built in M13.
 */
@Transactional
public class EraseExpiredExecutionDataChange {

    private final DeliveryRepository deliveries;
    private final PositionLog positions;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transactions.
     *
     * @param deliveries where deliveries are kept
     * @param positions where positions are kept
     * @param clock the server's time
     * @param auditLog the audit trail
     */
    public EraseExpiredExecutionDataChange(
            DeliveryRepository deliveries, PositionLog positions, Clock clock, AuditLog auditLog) {
        this.deliveries = deliveries;
        this.positions = positions;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Deletes the positions received 7 days ago or more, and writes the audit record (NFR-184).
     *
     * <p>Built in M13.
     *
     * @param call the job's server step
     * @return how many positions were deleted
     */
    public int deletePositions(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }

    /**
     * The stops whose delivery holds personal data past its retention, for the job to erase one at
     * a time (NFR-31, ADR-107).
     *
     * <p>Built in M13.
     *
     * @return the stops, each once; none when nothing is due
     */
    @Transactional(readOnly = true)
    public List<StopId> dueStops() {
        throw new UnsupportedOperationException("Built in M13");
    }

    /**
     * Erases one stop's delivery through {@code Delivery.eraseExpired}, keeping every record and
     * kind, and writes the audit record. It loads the delivery again, since {@link #dueStops} read
     * it in a transaction that has ended.
     *
     * <p>Built in M13.
     *
     * @param stop the stop whose delivery is due
     * @param call the job's server step
     * @throws StaleVersion when another writer saved the delivery meanwhile; the job leaves it for
     *     the next night
     */
    public void apply(StopId stop, CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
