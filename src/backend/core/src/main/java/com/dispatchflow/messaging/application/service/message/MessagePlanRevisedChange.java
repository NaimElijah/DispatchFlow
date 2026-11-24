package com.dispatchflow.messaging.application.service.message;

import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.api.ArrivalProjections;
import com.dispatchflow.liveops.application.api.ProgressFact;
import com.dispatchflow.liveops.application.api.StopArrivalFact;
import com.dispatchflow.messaging.application.port.out.MessagingSettingsRepository;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.messaging.application.port.out.StopMessagesRepository;
import com.dispatchflow.orders.application.api.RecipientContactFact;
import com.dispatchflow.orders.application.api.RecipientContacts;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link MessagePlanRevised}: a read-only one reads the stops the revision
 * changed, with their arrivals or progress and their phones, and each stop's {@code
 * StopMessages.onJoinedStartedRoute} or {@code onDeliveryChanged} runs in one of its own with the
 * audit record, so one stale save doesn't hold back the revision's other stops (ADR-129 decision
 * 3). The conditions come from Access's {@link Company}, the settings and the phone's opt-out
 * history, read in each stop's transaction.
 *
 * <p>To Be Implemented. Rows: FR-354, FR-372.
 */
@Transactional
public class MessagePlanRevisedChange {

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
     * @param publishedRoutes Planning's routes in force, for the changed routes' stops and orders
     * @param arrivalProjections Live Operations' projected arrivals, and the progress of stops the
     *     revision dropped
     * @param ids new IDs, for the messages
     * @param auditLog the audit trail
     */
    public MessagePlanRevisedChange(
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
     * Reads the stops the revision changed through {@code PublishedRoutes.changesIn} and the routes
     * in force, each joined stop's arrival from {@link ArrivalProjections#of}, each dropped stop's
     * progress from {@link ArrivalProjections#progressOf} with the route that held it, and the
     * phones from {@link RecipientContacts} in one call for the stops' orders. A joined stop with
     * no projection, which a later revision already took off, is left out.
     *
     * <p>To Be Implemented.
     *
     * @param event the revision
     * @param now the time the arrivals are projected for
     * @return the stops to trigger
     * @throws NotFound when a stop's order is gone, so the delivery is retried
     */
    @Transactional(readOnly = true)
    public Triggers stopsOf(PlanRevised event, Instant now) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Triggers <em>out for delivery</em> for a stop that joined a route in progress (FR-372), to
     * the phone {@code RecipientPhone.choose} picks from the order's contacts (FR-348).
     *
     * <p>To Be Implemented.
     *
     * @param stop the joined stop, as {@link #stopsOf} read it
     * @param now when the trigger is recorded, the time the arrival was projected for
     * @param call the listener's server step
     * @throws StaleVersion when another trigger or the sender saved the stop's messages meanwhile;
     *     the listener adapter retries the event at once, up to three times (DL §11)
     */
    public void joined(Joined stop, Instant now, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Triggers <em>your delivery changed</em> for a stop the revision dropped from a started route
     * (FR-354), to the phone {@code RecipientPhone.choose} picks from the order's contacts
     * (FR-348).
     *
     * <p>To Be Implemented.
     *
     * @param stop the dropped stop, as {@link #stopsOf} read it
     * @param now when the trigger is recorded
     * @param call the listener's server step
     * @throws StaleVersion when another trigger or the sender saved the stop's messages meanwhile;
     *     the listener adapter retries the event at once, up to three times (DL §11)
     */
    public void dropped(Dropped stop, Instant now, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * The stops a revision triggers, as {@link #stopsOf} read them. They hold phones, so it prints
     * redacted.
     *
     * @param joined the stops that joined a route in progress
     * @param dropped the stops the revision dropped from a started route
     */
    public record Triggers(List<Joined> joined, List<Dropped> dropped) implements PersonalData {

        /** Checks the parts are present, and keeps its own copies of the lists. */
        public Triggers {
            Objects.requireNonNull(joined, "joined");
            Objects.requireNonNull(dropped, "dropped");
            joined = List.copyOf(joined);
            dropped = List.copyOf(dropped);
        }

        /**
         * A form that hides the phones.
         *
         * @return {@code MessagePlanRevisedChange.Triggers[redacted]}
         */
        @Override
        public String toString() {
            return "MessagePlanRevisedChange.Triggers[redacted]";
        }
    }

    /**
     * A stop that joined a route in progress. It holds the phones, so it prints redacted.
     *
     * @param arrival the stop's arrival and progress on its new route, from Live Operations
     * @param contact the phones of the stop's order, from Customers &amp; Orders
     */
    public record Joined(StopArrivalFact arrival, RecipientContactFact contact) implements PersonalData {

        /** Checks every part is present. */
        public Joined {
            Objects.requireNonNull(arrival, "arrival");
            Objects.requireNonNull(contact, "contact");
        }

        /**
         * A form that hides the phones.
         *
         * @return {@code MessagePlanRevisedChange.Joined[redacted]}
         */
        @Override
        public String toString() {
            return "MessagePlanRevisedChange.Joined[redacted]";
        }
    }

    /**
     * A stop the revision dropped from a started route. It holds the phones, so it prints redacted.
     *
     * @param stop the dropped stop
     * @param progress how far the route that held it, and the stop, have come, from Live Operations
     * @param contact the phones of the stop's order, from Customers &amp; Orders
     */
    public record Dropped(StopId stop, ProgressFact progress, RecipientContactFact contact) implements PersonalData {

        /** Checks every part is present. */
        public Dropped {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(progress, "progress");
            Objects.requireNonNull(contact, "contact");
        }

        /**
         * A form that hides the phones.
         *
         * @return {@code MessagePlanRevisedChange.Dropped[redacted]}
         */
        @Override
        public String toString() {
            return "MessagePlanRevisedChange.Dropped[redacted]";
        }
    }
}
