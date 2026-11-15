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
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * Messaging's minute job: it asks Live Operations' {@link ArrivalProjections#projectedOn} for
 * today's stops, because projections change as time passes and raise no event (DL §7.4, §10.3), and
 * calls {@code StopMessages.onArrivalProjected} for each stop that awaits delivery, which may add
 * <em>arriving soon</em> and <em>time changed</em> (FR-352, FR-353, FR-384). Each answer becomes the
 * domain's {@code ArrivalFacts} and {@code ProjectedArrival} (DL §2.3 rule 1); the stops' orders come
 * from Planning's {@link PublishedRoutes}, their phones from {@link RecipientContacts}. Each stop
 * changes in a transaction of its own, with the audit record.
 *
 * <p>Access's {@link Company#profile()} is empty before the first Admin sets the company up. With no
 * profile there is no sender name, so the run adds no message and answers 0.
 *
 * <p>Built in M12. Rows: FR-352, FR-353, FR-384.
 */
public final class ProjectArrivals {

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
     * Creates the job.
     *
     * @param stopMessages where stops' messages are kept
     * @param settings where the messaging settings are kept
     * @param optOuts the phones' opt-out history
     * @param phoneHasher the keyed phone hash
     * @param company Access's company profile, for the name and language
     * @param recipientContacts Customers &amp; Orders' recipient phones
     * @param publishedRoutes Planning's routes in force, for each stop's order
     * @param arrivalProjections Live Operations' projected arrivals
     * @param clock the server's time and today's date
     * @param ids new IDs, for the messages
     * @param auditLog the audit trail
     */
    public ProjectArrivals(
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
     * Projects today's arrivals and adds the messages now due. A stop another writer saved meanwhile
     * is judged again on the next run.
     *
     * <p>Built in M12.
     *
     * @param call the job's server step
     * @return how many messages it added
     */
    public int projectDue(CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
