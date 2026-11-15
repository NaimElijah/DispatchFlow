package com.dispatchflow.messaging.application.service;

import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.api.ArrivalProjections;
import com.dispatchflow.liveops.events.RouteStarted;
import com.dispatchflow.messaging.application.port.out.MessagingSettingsRepository;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.orders.application.api.RecipientContacts;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Messaging's listener on Live Operations' {@code RouteStarted}: each stop of the route not yet
 * arrived at gets <em>out for delivery</em>, with its projected arrival rounded to the nearest
 * quarter hour (FR-351, FR-373). The route's stops and orders come from Planning's {@link
 * PublishedRoutes}, each stop's arrival and progress from Live Operations' {@link
 * ArrivalProjections#of}, the phones from Customers &amp; Orders' {@link RecipientContacts} in one
 * call for the route's orders, and the conditions from Access's {@link Company}, the settings and
 * the phone's opt-out history. A stop the plan took off meanwhile has no projection, and gets no
 * message. Each stop's
 * {@code StopMessages.onRouteStarted} runs in a transaction of its own, with the audit record.
 *
 * <p>Delivery is at least once (ADR-064); a stop gets each message at most once (NFR-191), so a
 * repeat adds nothing and it records no {@code ProcessedEvents} pair.
 *
 * <p>Access's {@link Company#profile()} is empty before the first Admin sets the company up. With no
 * profile there is no sender name, so the trigger adds no message and changes nothing.
 *
 * <p>Built in M12. Rows: FR-351, FR-373.
 */
public final class MessageRouteStarted {

    private final StopMessagesRepository stopMessages;
    private final MessagingSettingsRepository settings;
    private final OptOutList optOuts;
    private final PhoneHasher phoneHasher;
    private final Company company;
    private final RecipientContacts recipientContacts;
    private final PublishedRoutes publishedRoutes;
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
     * @param publishedRoutes Planning's routes in force, for the route's stops and orders
     * @param arrivalProjections Live Operations' projected arrivals
     * @param clock the server's time
     * @param ids new IDs, for the messages
     * @param auditLog the audit trail
     */
    public MessageRouteStarted(
            StopMessagesRepository stopMessages,
            MessagingSettingsRepository settings,
            OptOutList optOuts,
            PhoneHasher phoneHasher,
            Company company,
            RecipientContacts recipientContacts,
            PublishedRoutes publishedRoutes,
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
        this.publishedRoutes = publishedRoutes;
        this.arrivalProjections = arrivalProjections;
        this.clock = clock;
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Triggers <em>out for delivery</em> for the route's stops.
     *
     * <p>Built in M12.
     *
     * @param event the started route
     * @param call the listener's server step
     * @throws NotFound when a stop's order is gone, so the delivery is retried
     * @throws StaleVersion when another trigger or the sender saved the stop's messages meanwhile;
     *     the listener adapter retries the event at once, up to three times (DL §11)
     */
    public void on(RouteStarted event, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
