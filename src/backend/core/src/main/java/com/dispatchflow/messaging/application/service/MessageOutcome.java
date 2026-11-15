package com.dispatchflow.messaging.application.service;

import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.events.OutcomeRecorded;
import com.dispatchflow.messaging.application.port.out.MessagingSettingsRepository;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.orders.application.api.RecipientContacts;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;

/**
 * Messaging's listener on Live Operations' {@code OutcomeRecorded}, the stop's first outcome:
 * <em>delivered</em>, saying whether it was left at the door and, while the rating question is on,
 * asking for a rating; or <em>missed you</em>, with the dispatch phone and not the reason (FR-355,
 * FR-356, FR-374). The event's outcome becomes the domain's {@code ReportedOutcome}, the phone comes
 * from Customers &amp; Orders' {@link RecipientContacts} by the event's order, and one transaction
 * calls {@code StopMessages.onOutcome} with the audit record. A correction raises no event, so it
 * sends nothing (FR-374).
 *
 * <p>Delivery is at least once (ADR-064); a stop gets one of the two at most, so it records no
 * {@code ProcessedEvents} pair.
 *
 * <p>Built in M12. Rows: FR-355, FR-356, FR-374.
 */
public final class MessageOutcome {

    private final StopMessagesRepository stopMessages;
    private final MessagingSettingsRepository settings;
    private final OptOutList optOuts;
    private final PhoneHasher phoneHasher;
    private final Company company;
    private final RecipientContacts recipientContacts;
    private final Clock clock;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the listener's use case.
     *
     * @param stopMessages where stops' messages are kept
     * @param settings where the messaging settings are kept
     * @param optOuts the phones' opt-out history
     * @param phoneHasher the keyed phone hash
     * @param company Access's company profile, for the name and language
     * @param recipientContacts Customers &amp; Orders' recipient phones
     * @param clock the server's time
     * @param ids new IDs, for the message
     * @param auditLog the audit trail
     */
    public MessageOutcome(
            StopMessagesRepository stopMessages,
            MessagingSettingsRepository settings,
            OptOutList optOuts,
            PhoneHasher phoneHasher,
            Company company,
            RecipientContacts recipientContacts,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.stopMessages = stopMessages;
        this.settings = settings;
        this.optOuts = optOuts;
        this.phoneHasher = phoneHasher;
        this.company = company;
        this.recipientContacts = recipientContacts;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Triggers <em>delivered</em> or <em>missed you</em> for the stop.
     *
     * <p>Built in M12.
     *
     * @param event the first outcome
     * @param call the listener's server step
     * @throws NotFound when the company has no profile yet, or the order is gone, so the delivery is
     *     retried
     */
    public void on(OutcomeRecorded event, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
