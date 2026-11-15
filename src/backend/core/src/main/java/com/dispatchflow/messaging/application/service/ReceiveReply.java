package com.dispatchflow.messaging.application.service;

import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.messaging.application.port.out.MessagingSettingsRepository;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.messaging.application.port.out.RatingRequestRepository;
import com.dispatchflow.messaging.application.port.out.ReplyLog;
import com.dispatchflow.messaging.application.port.out.SmsGateway;
import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;

/**
 * The SMS provider's callback with a recipient's reply (FR-357 to FR-360, FR-363, FR-371, FR-376,
 * FR-379). {@code Reply.parse} reads it by fixed rules. An opt-out word opts the phone out at once
 * through {@code OptOutStatus.optOutByReply}, which needs the last accepted message to the phone
 * (FR-363); any other reply answers the latest rating request sent to the phone within 48 hours
 * through {@code RatingRequest.answer}, and a reply with no such request is discarded and not kept
 * (FR-379). The {@code SendingConditions} come from Access's {@link Company}, the {@code
 * MessagingSettings} and the phone's opt-out history.
 *
 * <p>The reply is kept, unique by the provider's identifier, in the transaction of its effect, so a
 * call received twice takes effect once (NFR-199), and the callback is answered only after that
 * commit (NFR-201). The thank-you, the format reminder and the opt-out confirmation the domain says
 * are due are then sent through {@link SmsGateway}, after the commit and outside it (placement P10,
 * ADR-108); one that fails is not kept for later.
 *
 * <p>Each answer counts toward the day's cap of 1,000 messages (NFR-196, ADR-108 Risk). In the
 * transaction of the reply's effect, it counts the answer through {@link
 * StopMessagesRepository#countAnswer} for today's Israel date from the {@link Clock}, before
 * sending it. The count is conditional and atomic: it counts only while the cap has room, under the
 * same per-day lock as the sender's claim, and answers whether it counted. An answer not counted is
 * skipped for good, as an answer due when messages can't go to the phone is (ADR-108), since each
 * is sent once.
 *
 * <p><b>Precondition.</b> The callback's adapter has accepted the call only with the provider's
 * signature or header secret, one of two while one is rotated, compared in constant time, before it
 * calls this; any other call is refused there and changes nothing (NFR-192). This use case trusts
 * that its arguments came from the provider.
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Access's {@link Company#profile()} is empty before the first Admin sets the company up. With
 * no profile there is no sender name, so no message was ever sent and no rating request is open: an
 * opt-out word still opts the phone out, any other reply is discarded, and no answer is sent.
 *
 * <p>Built in M12. Rows: FR-357 to FR-360, FR-363, FR-371, FR-376, FR-379, NFR-192, NFR-196,
 * NFR-199, NFR-201, NFR-203.
 */
public final class ReceiveReply {

    private final ReplyLog replies;
    private final RatingRequestRepository ratingRequests;
    private final StopMessagesRepository stopMessages;
    private final OptOutList optOuts;
    private final PhoneHasher phoneHasher;
    private final MessagingSettingsRepository settings;
    private final Company company;
    private final SmsGateway smsGateway;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param replies where replies are kept
     * @param ratingRequests where rating requests are kept
     * @param stopMessages where stops' messages are kept, for the phone's last accepted message and
     *     the day's cap, which the answers count toward
     * @param optOuts the phones' opt-out history
     * @param phoneHasher the keyed phone hash
     * @param settings where the messaging settings are kept
     * @param company Access's company profile, for the name and language the answers are written in
     * @param smsGateway the SMS provider, for the answers
     * @param clock today's Israel date, for the cap
     * @param ids new IDs, for each answer sent
     * @param auditLog the audit trail
     */
    public ReceiveReply(
            ReplyLog replies,
            RatingRequestRepository ratingRequests,
            StopMessagesRepository stopMessages,
            OptOutList optOuts,
            PhoneHasher phoneHasher,
            MessagingSettingsRepository settings,
            Company company,
            SmsGateway smsGateway,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.replies = replies;
        this.ratingRequests = ratingRequests;
        this.stopMessages = stopMessages;
        this.optOuts = optOuts;
        this.phoneHasher = phoneHasher;
        this.settings = settings;
        this.company = company;
        this.smsGateway = smsGateway;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Takes one reply. The caller has checked the provider's signature or secret (NFR-192).
     *
     * <p>Built in M12.
     *
     * @param providerId the provider's identifier for the inbound message (NFR-199)
     * @param from the phone it came from, as the adapter parsed it
     * @param text the reply's text, as delivered; a comment is cut to 500 characters (NFR-203)
     * @param receivedAt when the provider says it was received
     * @param call the callback's server step
     * @throws StaleVersion when another reply saved the rating request meanwhile; the web adapter
     *     retries the call at once, up to three times, then answers 503 with {@code Retry-After},
     *     and the provider sends the call again (ADR-121, NFR-201)
     */
    public void receive(String providerId, PhoneNumber from, String text, Instant receivedAt, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
