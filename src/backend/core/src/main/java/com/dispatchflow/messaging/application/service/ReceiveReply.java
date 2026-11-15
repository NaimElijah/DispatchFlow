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
 * <p>Built in M12. Rows: FR-357 to FR-360, FR-363, FR-371, FR-376, FR-379, NFR-199, NFR-201, NFR-203.
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
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param replies where replies are kept
     * @param ratingRequests where rating requests are kept
     * @param stopMessages where stops' messages are kept, for the phone's last accepted message
     * @param optOuts the phones' opt-out history
     * @param phoneHasher the keyed phone hash
     * @param settings where the messaging settings are kept
     * @param company Access's company profile, for the name and language the answers are written in
     * @param smsGateway the SMS provider, for the answers
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
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Takes one reply.
     *
     * <p>Built in M12.
     *
     * @param providerId the provider's identifier for the inbound message (NFR-199)
     * @param from the phone it came from, as the adapter parsed it
     * @param text the reply's text, as delivered; a comment is cut to 500 characters (NFR-203)
     * @param receivedAt when the provider says it was received
     * @param call the callback's server step
     * @throws StaleVersion when another reply saved the rating request meanwhile; the provider sends
     *     the call again (NFR-201)
     */
    public void receive(String providerId, PhoneNumber from, String text, Instant receivedAt, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
