package com.dispatchflow.messaging.application.service;

import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.messaging.application.port.out.MessagingSettingsRepository;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.messaging.application.port.out.RatingRequestRepository;
import com.dispatchflow.messaging.application.port.out.ReplyLog;
import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.messaging.domain.MessageText;
import com.dispatchflow.messaging.domain.OutgoingMessageId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of a reply's effect in {@link ReceiveReply}, which holds none of its outside
 * calls (ADR-129 decision 3): it keeps the reply, unique by the provider's identifier (NFR-199),
 * applies its opt-out or its answer to a rating request, counts each answer the domain says is due
 * toward the day's cap through {@link StopMessagesRepository#countAnswer}, after it saves what the
 * reply changed, and writes the audit record. It answers the counted messages, which {@link
 * ReceiveReply} sends after the commit.
 *
 * <p>To Be Implemented. Rows: FR-357 to FR-360, FR-363, FR-371, FR-376, FR-379, NFR-196, NFR-199,
 * NFR-203.
 */
@Transactional
public class ReceiveReplyChange {

    private final ReplyLog replies;
    private final RatingRequestRepository ratingRequests;
    private final StopMessagesRepository stopMessages;
    private final OptOutList optOuts;
    private final PhoneHasher phoneHasher;
    private final MessagingSettingsRepository settings;
    private final Company company;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the use case's transaction.
     *
     * @param replies where replies are kept
     * @param ratingRequests where rating requests are kept
     * @param stopMessages where stops' messages are kept, for the phone's last accepted message and
     *     the day's cap, which the answers count toward
     * @param optOuts the phones' opt-out history
     * @param phoneHasher the keyed phone hash
     * @param settings where the messaging settings are kept
     * @param company Access's company profile, for the name and language the answers are written in
     * @param clock today's Israel date, for the cap
     * @param ids new IDs, for each answer sent
     * @param auditLog the audit trail
     */
    public ReceiveReplyChange(
            ReplyLog replies,
            RatingRequestRepository ratingRequests,
            StopMessagesRepository stopMessages,
            OptOutList optOuts,
            PhoneHasher phoneHasher,
            MessagingSettingsRepository settings,
            Company company,
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
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Applies one reply. A call received before, by the provider's identifier, changes nothing and
     * answers no message.
     *
     * <p>To Be Implemented.
     *
     * @param providerId the provider's identifier for the inbound message (NFR-199)
     * @param from the phone it came from, as the adapter parsed it
     * @param text the reply's text, as delivered; a comment is cut to 500 characters (NFR-203)
     * @param receivedAt when the provider says it was received
     * @param call the callback's server step
     * @return the thank-you, format reminder or opt-out confirmation due and counted toward the
     *     day's cap, in the order to send them; none when no answer is due or the cap is reached
     * @throws StaleVersion when another reply saved the rating request meanwhile
     */
    public List<Answer> apply(String providerId, PhoneNumber from, String text, Instant receivedAt, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * One answer to send after the commit. It holds the phone, so it prints redacted.
     *
     * @param message the answer's reference, which counted toward the day's cap
     * @param to the phone it goes to
     * @param text its text, one SMS
     */
    public record Answer(OutgoingMessageId message, PhoneNumber to, MessageText text) implements PersonalData {

        /** Checks every part is present. */
        public Answer {
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(text, "text");
        }

        /**
         * A form that hides the phone.
         *
         * @return {@code ReceiveReplyChange.Answer[redacted]}
         */
        @Override
        public String toString() {
            return "ReceiveReplyChange.Answer[redacted]";
        }
    }
}
