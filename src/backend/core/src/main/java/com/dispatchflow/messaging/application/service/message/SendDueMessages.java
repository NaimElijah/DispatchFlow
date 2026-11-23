package com.dispatchflow.messaging.application.service.message;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.messaging.application.port.out.SmsGateway;

/**
 * The sender, every 5 seconds (DL §9.5, §10.3, §12): for each stop with a message to send it drops
 * the stale ones ({@code StopMessages.dropStale}, NFR-190), claims each pending message against the
 * day's {@code DailySendCap} and its own phone's {@code PhoneConsent} (NFR-196, FR-363, ADR-117),
 * reading the cap through {@code StopMessagesRepository.capOn} under the day's lock, which a
 * reply's answer count takes too, commits the claim, calls {@link SmsGateway#send} outside any
 * transaction, and records the answer through {@code accepted}, {@code refused} or {@code
 * outcomeUnknown} (NFR-198). A <em>delivered</em> message that asks for a rating opens its {@code
 * RatingRequest} in the transaction of its acceptance (DL §6.2). Each step writes its audit record.
 * Each run returns a {@link SendRun} with the number dropped, which the job adapter measures
 * (NFR-190, NFR-161).
 *
 * <p>The {@link SmsGateway} is an outside call, so this class opens no transaction: {@link
 * SendDueMessagesChange} lists the stops, claims each stop's messages in one transaction, and
 * records each answer in one of its own (ADR-129).
 *
 * <p>Built in M12. Rows: NFR-190, NFR-191, NFR-198.
 */
public final class SendDueMessages {

    private final SmsGateway smsGateway;
    private final SendDueMessagesChange change;

    /**
     * Creates the job.
     *
     * @param smsGateway the SMS provider
     * @param change the transactions that list the stops, claim their messages and record each
     *     answer
     */
    public SendDueMessages(SmsGateway smsGateway, SendDueMessagesChange change) {
        this.smsGateway = smsGateway;
        this.change = change;
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
