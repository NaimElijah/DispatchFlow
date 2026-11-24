package com.dispatchflow.messaging.application.service.message;

import com.dispatchflow.access.application.api.Company;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.api.ArrivalProjections;
import com.dispatchflow.orders.application.api.RecipientContacts;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.planning.events.PlanRevised;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Messaging's listener on Planning's {@code PlanRevised}: on the routes the revision changed, a stop
 * that joined a route in progress gets <em>out for delivery</em> through {@code
 * StopMessages.onJoinedStartedRoute} (FR-372), and a stop the revision dropped from a started route
 * gets <em>your delivery changed</em> through {@code onDeliveryChanged} (FR-354). The current routes
 * come from Planning's {@link PublishedRoutes}, and the phones from Customers &amp; Orders' {@link
 * RecipientContacts} in one call for the stops' orders; each stop changes in a transaction of its
 * own, with the audit record.
 *
 * <p>A joined stop's arrival and progress come from Live Operations' {@link ArrivalProjections#of};
 * an empty answer means a later revision already took the stop off, and it gets no message. A
 * dropped stop is no longer on any route in force, so its progress comes from {@link
 * ArrivalProjections#progressOf}, with the route that held it, which reads Live Operations' own
 * run and delivery.
 *
 * <p>The stops the revision added, moved or took off come from {@code PublishedRoutes.changesIn}.
 *
 * <p>Delivery is at least once (ADR-064); each message goes at most once per stop (NFR-191), so a
 * repeat adds nothing and it records no {@code ProcessedEvents} pair.
 *
 * <p>Access's {@link Company#profile()} is empty before the first Admin sets the company up. With no
 * profile there is no sender name, so the trigger adds no message and changes nothing.
 *
 * <p>It makes no outside call and opens no transaction of its own: {@link
 * MessagePlanRevisedChange#stopsOf} reads the changed routes, the arrivals, the progress and the
 * phones in a read-only one, and {@link MessagePlanRevisedChange#joined} and {@link
 * MessagePlanRevisedChange#dropped} trigger each stop in one of its own (ADR-129).
 *
 * <p>Built in M12. Rows: FR-354, FR-372.
 */
public final class MessagePlanRevised {

    private final Clock clock;
    private final MessagePlanRevisedChange change;

    /**
     * Creates the listener's use case.
     *
     * @param clock the server's time
     * @param change the read of the revision's stops, and the transaction of each stop's trigger
     */
    public MessagePlanRevised(Clock clock, MessagePlanRevisedChange change) {
        this.clock = clock;
        this.change = change;
    }

    /**
     * Triggers the messages the revision calls for.
     *
     * <p>Built in M12.
     *
     * @param event the revision
     * @param call the listener's server step
     * @throws NotFound when a stop's order is gone, so the delivery is retried
     * @throws StaleVersion when another trigger or the sender saved the stop's messages meanwhile;
     *     the listener adapter retries the event at once, up to three times (DL §11)
     */
    public void on(PlanRevised event, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
