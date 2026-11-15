package com.dispatchflow.messaging.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.messaging.application.port.out.RatingRequestRepository;
import com.dispatchflow.messaging.application.port.out.ReplyLog;
import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.shared.application.Clock;

/**
 * The nightly retention job's Messaging step (NFR-184, NFR-193, NFR-260, ADR-075, ADR-108): it
 * deletes each stop's messages 90 days after the last reached its state, deletes replies 90 days
 * after receipt, and removes each due rating request's phone and comments through {@code
 * RatingRequest.removePersonalData}, one request per transaction, keeping its rating. The opt-out
 * history stays (NFR-202). The scheduler calls it at 03:00 {@code Asia/Jerusalem} (DL §10.3).
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121). Rows: NFR-184, NFR-193, NFR-260,
 * ADR-108.
 */
public final class EraseExpiredMessages {

    private final StopMessagesRepository stopMessages;
    private final RatingRequestRepository ratingRequests;
    private final ReplyLog replies;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param stopMessages where stops' messages are kept
     * @param ratingRequests where rating requests are kept
     * @param replies where replies are kept
     * @param clock the server's time
     * @param auditLog the audit trail
     */
    public EraseExpiredMessages(
            StopMessagesRepository stopMessages,
            RatingRequestRepository ratingRequests,
            ReplyLog replies,
            Clock clock,
            AuditLog auditLog) {
        this.stopMessages = stopMessages;
        this.ratingRequests = ratingRequests;
        this.replies = replies;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Erases what is due. A request another writer saved meanwhile is left for the next night.
     *
     * <p>Built in M13.
     *
     * @param call the job's server step
     * @return how many stops' messages, replies and requests it deleted or cleared, together
     */
    public int erase(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
