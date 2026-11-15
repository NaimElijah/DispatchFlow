package com.dispatchflow.messaging.application.service;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.messaging.application.port.out.RatingRequestRepository;
import com.dispatchflow.messaging.application.port.out.SmsGateway;
import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * The sender, every 5 seconds (DL §9.5, §10.3, §12): for each stop with a message to send it drops
 * the stale ones ({@code StopMessages.dropStale}, NFR-190), claims each pending message against the
 * day's {@code DailySendCap} and its own phone's {@code PhoneConsent} (NFR-196, FR-363, ADR-117),
 * commits the claim, calls {@link SmsGateway#send} outside any transaction, and records the answer
 * through {@code accepted}, {@code refused} or {@code outcomeUnknown} (NFR-198). A
 * <em>delivered</em> message that asks for a rating opens its {@code RatingRequest} in the
 * transaction of its acceptance (DL §6.2). Each step writes its audit record. Each run returns a
 * {@link SendRun} with the number dropped, which the job adapter measures (NFR-190, NFR-161).
 *
 * <p>Built in M12. Rows: NFR-190, NFR-191, NFR-198.
 */
public final class SendDueMessages {

    private final StopMessagesRepository stopMessages;
    private final RatingRequestRepository ratingRequests;
    private final OptOutList optOuts;
    private final PhoneHasher phoneHasher;
    private final SmsGateway smsGateway;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the job.
     *
     * @param stopMessages where stops' messages are kept
     * @param ratingRequests where rating requests are kept
     * @param optOuts the phones' opt-out history
     * @param phoneHasher the keyed phone hash
     * @param smsGateway the SMS provider
     * @param clock the server's time and today's Israel date, for the cap
     * @param ids new IDs, for the rating requests
     * @param auditLog the audit trail
     */
    public SendDueMessages(
            StopMessagesRepository stopMessages,
            RatingRequestRepository ratingRequests,
            OptOutList optOuts,
            PhoneHasher phoneHasher,
            SmsGateway smsGateway,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.stopMessages = stopMessages;
        this.ratingRequests = ratingRequests;
        this.optOuts = optOuts;
        this.phoneHasher = phoneHasher;
        this.smsGateway = smsGateway;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Sends the messages now due. A claim another writer beat is retried on the next run (DL §11).
     *
     * <p>Built in M12.
     *
     * @param call the job's server step
     * @return how many messages the provider accepted, and how many were dropped as stale (NFR-190)
     */
    public SendRun sendDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
