package com.dispatchflow.messaging.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.messaging.application.port.out.RatingRequestRepository;
import com.dispatchflow.messaging.application.port.out.ReplyLog;
import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link EraseExpiredMessages}: one deletes the stops' messages and the replies
 * whose retention is over, one lists the rating requests due, and each request is cleared in one of
 * its own, with its audit record, so one stale save doesn't hold back the rest (ADR-129 decision
 * 3).
 *
 * <p>Built in M13, with the whole nightly retention run (ADR-121). Rows: NFR-184, NFR-193, NFR-260,
 * ADR-108.
 */
@Transactional
public class EraseExpiredMessagesChange {

    private final StopMessagesRepository stopMessages;
    private final RatingRequestRepository ratingRequests;
    private final ReplyLog replies;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the job's transactions.
     *
     * @param stopMessages where stops' messages are kept
     * @param ratingRequests where rating requests are kept
     * @param replies where replies are kept
     * @param clock the server's time
     * @param auditLog the audit trail
     */
    public EraseExpiredMessagesChange(
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
     * Deletes each stop's messages 90 days after the last reached its state, and the replies 90
     * days after receipt.
     *
     * <p>Built in M13.
     *
     * @param call the job's server step
     * @return how many stops' messages and replies it deleted, together
     */
    public int deleteExpired(CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }

    /**
     * The rating requests holding personal data whose retention has ended.
     *
     * <p>Built in M13.
     *
     * @return the requests, each once
     */
    @Transactional(readOnly = true)
    public List<RatingRequestId> due() {
        throw new UnsupportedOperationException("Built in M13");
    }

    /**
     * Removes one request's phone and comments through {@code RatingRequest.removePersonalData},
     * keeping its rating, and writes its audit record.
     *
     * <p>Built in M13.
     *
     * @param request the rating request
     * @param call the job's server step
     * @throws RuleViolation with Messaging's {@code RetentionNotOver} when personal data is left
     *     and none of it is due yet
     * @throws StaleVersion when another writer saved the request meanwhile; the next night retries
     *     it
     */
    public void apply(RatingRequestId request, CallContext call) {
        throw new UnsupportedOperationException("Built in M13");
    }
}
