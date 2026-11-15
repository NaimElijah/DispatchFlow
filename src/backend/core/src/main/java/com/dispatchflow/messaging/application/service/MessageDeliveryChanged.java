package com.dispatchflow.messaging.application.service;

import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.api.ArrivalProjections;
import com.dispatchflow.messaging.application.port.out.MessagingSettingsRepository;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.orders.application.api.RecipientContacts;
import com.dispatchflow.orders.events.OrderCancelled;
import com.dispatchflow.orders.events.OrderRescheduled;
import com.dispatchflow.planning.application.api.RouteMembership;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Messaging's listener on Customers &amp; Orders' {@code OrderCancelled} and {@code
 * OrderRescheduled}: once the stop's route has started, the recipient gets <em>your delivery
 * changed</em> with the dispatch phone, unless the stop is arrived at or has an outcome (FR-354,
 * FR-373). One transaction calls {@code StopMessages.onDeliveryChanged} for the order's stop on the
 * date it left, with the route's and the stop's progress from Live Operations' {@link
 * ArrivalProjections}, and the audit record.
 *
 * <p>Planning's own listener takes the stop off the plan on the same event, so the stop may already
 * be gone from the plan when this runs; {@code RouteMembership.stopOf} finds it, and the route that
 * held it, in the stored revisions.
 *
 * <p>Delivery is at least once (ADR-064); the message goes at most once per stop (NFR-191), so it
 * records no {@code ProcessedEvents} pair.
 *
 * <p>Access's {@link Company#profile()} is empty before the first Admin sets the company up. With no
 * profile there is no sender name, so the trigger adds no message and changes nothing.
 *
 * <p>Built in M12. Rows: FR-354.
 */
public final class MessageDeliveryChanged {

    private final StopMessagesRepository stopMessages;
    private final MessagingSettingsRepository settings;
    private final OptOutList optOuts;
    private final PhoneHasher phoneHasher;
    private final Company company;
    private final RecipientContacts recipientContacts;
    private final RouteMembership routeMembership;
    private final ArrivalProjections arrivalProjections;
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
     * @param routeMembership Planning's stored revisions, for the order's stop on the date and its route
     * @param arrivalProjections Live Operations' route and stop progress
     * @param clock the server's time
     * @param ids new IDs, for the message
     * @param auditLog the audit trail
     */
    public MessageDeliveryChanged(
            StopMessagesRepository stopMessages,
            MessagingSettingsRepository settings,
            OptOutList optOuts,
            PhoneHasher phoneHasher,
            Company company,
            RecipientContacts recipientContacts,
            RouteMembership routeMembership,
            ArrivalProjections arrivalProjections,
            Clock clock,
            IdGenerator ids,
            AuditLog auditLog) {
        this.stopMessages = stopMessages;
        this.settings = settings;
        this.optOuts = optOuts;
        this.phoneHasher = phoneHasher;
        this.company = company;
        this.recipientContacts = recipientContacts;
        this.routeMembership = routeMembership;
        this.arrivalProjections = arrivalProjections;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Triggers <em>your delivery changed</em> for a cancelled order's stop.
     *
     * <p>Built in M12.
     *
     * @param event the cancelled order
     * @param call the listener's server step
     * @throws StaleVersion when another trigger or the sender saved the stop's messages meanwhile;
     *     the listener adapter retries the event at once, up to three times (DL §11)
     */
    public void on(OrderCancelled event, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }

    /**
     * Triggers <em>your delivery changed</em> for a rescheduled order's stop on the date it left.
     *
     * <p>Built in M12.
     *
     * @param event the rescheduled order
     * @param call the listener's server step
     * @throws StaleVersion when another trigger or the sender saved the stop's messages meanwhile;
     *     the listener adapter retries the event at once, up to three times (DL §11)
     */
    public void on(OrderRescheduled event, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
