package com.dispatchflow.messaging.application.service.message;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.messaging.application.port.out.RatingRequestRepository;
import com.dispatchflow.messaging.application.port.out.SendAnswer;
import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.messaging.domain.MessageText;
import com.dispatchflow.messaging.domain.OutgoingMessageId;
import com.dispatchflow.messaging.domain.message.StopMessages.ProviderRepeats;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link SendDueMessages}, which hold none of its outside calls (ADR-129
 * decision 3): one lists the stops with a message to send; one per stop drops its stale messages
 * and claims its pending ones under the day's lock, and commits the claim before the send; and one
 * per message records the provider's answer, opening a {@code RatingRequest} in the transaction of
 * a <em>delivered</em> message's acceptance (DL §6.2). Each step that changes a stop writes its
 * audit record.
 *
 * <p>Built in M12. Rows: NFR-190, NFR-191, NFR-198.
 */
@Transactional
public class SendDueMessagesChange {

    private final StopMessagesRepository stopMessages;
    private final RatingRequestRepository ratingRequests;
    private final OptOutList optOuts;
    private final PhoneHasher phoneHasher;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the job's transactions.
     *
     * @param stopMessages where stops' messages are kept
     * @param ratingRequests where rating requests are kept
     * @param optOuts the phones' opt-out history
     * @param phoneHasher the keyed phone hash
     * @param clock the server's time and today's Israel date, for the cap
     * @param ids new IDs, for the rating requests
     * @param auditLog the audit trail
     */
    public SendDueMessagesChange(
            StopMessagesRepository stopMessages,
            RatingRequestRepository ratingRequests,
            OptOutList optOuts,
            PhoneHasher phoneHasher,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.stopMessages = stopMessages;
        this.ratingRequests = ratingRequests;
        this.optOuts = optOuts;
        this.phoneHasher = phoneHasher;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * The stops with a message to send.
     *
     * <p>Built in M12.
     *
     * @return the stops, each once, the oldest trigger first
     */
    @Transactional(readOnly = true)
    public List<StopId> pending() {
        throw new UnsupportedOperationException("Built in M12");
    }

    /**
     * Drops the stop's stale messages ({@code StopMessages.dropStale}, NFR-190), and claims each
     * pending one against the day's {@code DailySendCap}, read through {@code
     * StopMessagesRepository.capOn} under the day's lock, and its own phone's {@code PhoneConsent}
     * (NFR-196, FR-363, ADR-117).
     *
     * <p>Built in M12.
     *
     * @param stop the stop
     * @param call the job's server step
     * @return how many messages it dropped as stale, and the claimed messages to send
     * @throws StaleVersion when another writer saved the stop's messages meanwhile; the next run
     *     retries the claim (DL §11)
     */
    public Claimed claim(StopId stop, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }

    /**
     * Records the provider's answer to one claimed message through {@code StopMessages.accepted},
     * {@code refused} or {@code outcomeUnknown} (NFR-198). An accepted <em>delivered</em> message
     * that asks for a rating opens its {@code RatingRequest} here (DL §6.2).
     *
     * <p>Built in M12.
     *
     * @param stop the message's stop
     * @param message the message
     * @param answer what the provider answered
     * @param repeats whether the provider drops a repeated send, for an unknown outcome
     * @param call the job's server step
     * @throws StaleVersion when another writer saved the stop's messages meanwhile (DL §11)
     */
    public void record(
            StopId stop, OutgoingMessageId message, SendAnswer answer, ProviderRepeats repeats, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }

    /**
     * What one stop's claim did.
     *
     * @param dropped how many of the stop's messages it dropped as stale, 0 or more (NFR-190)
     * @param sends the messages it claimed, to send now
     */
    public record Claimed(int dropped, List<Send> sends) {

        /** Checks the parts are present, and keeps its own copy of the messages. */
        public Claimed {
            Objects.requireNonNull(sends, "sends");
            sends = List.copyOf(sends);
        }
    }

    /**
     * One claimed message to send. It holds the phone, so it prints redacted.
     *
     * @param message DispatchFlow's reference for the message (NFR-198)
     * @param to the phone it goes to
     * @param text its text, one SMS
     */
    public record Send(OutgoingMessageId message, PhoneNumber to, MessageText text) implements PersonalData {

        /** Checks every part is present. */
        public Send {
            Objects.requireNonNull(message, "message");
            Objects.requireNonNull(to, "to");
            Objects.requireNonNull(text, "text");
        }

        /**
         * A form that hides the phone.
         *
         * @return {@code SendDueMessagesChange.Send[redacted]}
         */
        @Override
        public String toString() {
            return "SendDueMessagesChange.Send[redacted]";
        }
    }
}
