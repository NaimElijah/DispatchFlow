package com.dispatchflow.messaging.application.service.message;

import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.api.ArrivalProjections;
import com.dispatchflow.liveops.application.api.StopArrivalFact;
import com.dispatchflow.liveops.events.RouteStarted;
import com.dispatchflow.messaging.application.port.out.MessagingSettingsRepository;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.orders.application.api.RecipientContactFact;
import com.dispatchflow.orders.application.api.RecipientContacts;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link MessageRouteStarted}: a read-only one reads the route's stops, their
 * arrivals and their phones, and each stop's {@code StopMessages.onRouteStarted} runs in one of its
 * own with the audit record, so one stale save doesn't hold back the route's other stops (ADR-129
 * decision 3). The conditions come from Access's {@link Company}, the settings and the phone's
 * opt-out history, read in each stop's transaction.
 *
 * <p>Built in M12. Rows: FR-351, FR-373.
 */
@Transactional
public class MessageRouteStartedChange {

    private final StopMessagesRepository stopMessages;
    private final MessagingSettingsRepository settings;
    private final OptOutList optOuts;
    private final PhoneHasher phoneHasher;
    private final Company company;
    private final RecipientContacts recipientContacts;
    private final PublishedRoutes publishedRoutes;
    private final ArrivalProjections arrivalProjections;
    private final IdGenerator ids;
    private final AuditLog auditLog;

    /**
     * Creates the listener's transactions.
     *
     * @param stopMessages where stops' messages are kept
     * @param settings where the messaging settings are kept
     * @param optOuts the phones' opt-out history
     * @param phoneHasher the keyed phone hash
     * @param company Access's company profile, for the name and language
     * @param recipientContacts Customers &amp; Orders' recipient phones
     * @param publishedRoutes Planning's routes in force, for the route's stops and orders
     * @param arrivalProjections Live Operations' projected arrivals
     * @param ids new IDs, for the messages
     * @param auditLog the audit trail
     */
    public MessageRouteStartedChange(
            StopMessagesRepository stopMessages,
            MessagingSettingsRepository settings,
            OptOutList optOuts,
            PhoneHasher phoneHasher,
            Company company,
            RecipientContacts recipientContacts,
            PublishedRoutes publishedRoutes,
            ArrivalProjections arrivalProjections,
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
        this.ids = ids;
        this.auditLog = auditLog;
    }

    /**
     * Reads the started route's stops and orders from {@link PublishedRoutes}, each stop's arrival
     * and progress from {@link ArrivalProjections#of}, and the phones from {@link
     * RecipientContacts} in one call for the route's orders. A stop the plan took off meanwhile has
     * no projection, and is left out.
     *
     * <p>Built in M12.
     *
     * @param event the started route
     * @param now the time the arrivals are projected for
     * @return the route's stops to trigger, each with its arrival and its order's phones
     * @throws NotFound when a stop's order is gone, so the delivery is retried
     */
    @Transactional(readOnly = true)
    public List<Stop> stopsOf(RouteStarted event, Instant now) {
        throw new UnsupportedOperationException("Built in M12");
    }

    /**
     * Triggers <em>out for delivery</em> for one stop of the route, with its projected arrival, to
     * the phone {@code RecipientPhone.choose} picks from the order's contacts (FR-348).
     *
     * <p>Built in M12.
     *
     * @param stop the stop, as {@link #stopsOf} read it
     * @param now when the trigger is recorded, the time the arrival was projected for
     * @param call the listener's server step
     * @throws StaleVersion when another trigger or the sender saved the stop's messages meanwhile;
     *     the listener adapter retries the event at once, up to three times (DL §11)
     */
    public void apply(Stop stop, Instant now, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }

    /**
     * One stop of the started route, as {@link #stopsOf} read it. It holds the phones, so it prints
     * redacted.
     *
     * @param arrival the stop's arrival and progress, from Live Operations
     * @param contact the phones of the stop's order, from Customers &amp; Orders
     */
    public record Stop(StopArrivalFact arrival, RecipientContactFact contact) implements PersonalData {

        /** Checks every part is present. */
        public Stop {
            Objects.requireNonNull(arrival, "arrival");
            Objects.requireNonNull(contact, "contact");
        }

        /**
         * A form that hides the phones.
         *
         * @return {@code MessageRouteStartedChange.Stop[redacted]}
         */
        @Override
        public String toString() {
            return "MessageRouteStartedChange.Stop[redacted]";
        }
    }
}
