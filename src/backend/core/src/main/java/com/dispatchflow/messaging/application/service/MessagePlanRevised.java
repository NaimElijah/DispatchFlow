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
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;

/**
 * Messaging's listener on Planning's {@code PlanRevised}: on the routes the revision changed, a stop
 * that joined a route in progress gets <em>out for delivery</em> through {@code
 * StopMessages.onJoinedStartedRoute} (FR-372), and a stop the revision dropped from a started route
 * gets <em>your delivery changed</em> through {@code onDeliveryChanged} (FR-354). The current routes
 * come from Planning's {@link PublishedRoutes}, and the arrivals and progress from Live Operations'
 * {@link ArrivalProjections}; each stop changes in a transaction of its own, with the audit record.
 *
 * <p>Planning's API answers the current routes only, so the stops a revision took off a route wait
 * for a Planning lookup that names them.
 *
 * <p>Delivery is at least once (ADR-064); each message goes at most once per stop (NFR-191), so a
 * repeat adds nothing and it records no {@code ProcessedEvents} pair.
 *
 * <p>Built in M12. Rows: FR-354, FR-372.
 */
public final class MessagePlanRevised {

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
     * @param publishedRoutes Planning's routes in force, for the changed routes' stops and orders
     * @param arrivalProjections Live Operations' projected arrivals and progress
     * @param clock the server's time
     * @param ids new IDs, for the messages
     * @param auditLog the audit trail
     */
    public MessagePlanRevised(
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
     * Triggers the messages the revision calls for.
     *
     * <p>Built in M12.
     *
     * @param event the revision
     * @param call the listener's server step
     * @throws NotFound when the company has no profile yet, or a stop's order is gone, so the
     *     delivery is retried
     */
    public void on(PlanRevised event, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
