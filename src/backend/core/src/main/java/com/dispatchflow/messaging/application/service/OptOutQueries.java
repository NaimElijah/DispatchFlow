package com.dispatchflow.messaging.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.messaging.application.port.out.MessagingViews;
import com.dispatchflow.messaging.application.port.out.OptOutList;
import com.dispatchflow.messaging.application.port.out.OptOutStandingView;
import com.dispatchflow.messaging.application.port.out.PhoneHasher;
import com.dispatchflow.messaging.application.port.out.WhyNoMessagesView;
import com.dispatchflow.orders.application.api.RecipientContacts;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.NotFound;

/**
 * What a dispatcher sees about a phone's messages: whether it opted out, with who made the latest
 * change and when (FR-380), and why a stop's recipient gets none (FR-378). The standing comes from
 * the phone's keyed hash and its history ({@code OptOutStatus}); the reason also reads the messaging
 * settings and the stop's phone, chosen from Customers &amp; Orders' {@link RecipientContacts} as
 * {@code RecipientPhone.choose} does (FR-348).
 *
 * <p>Planning's API has no lookup from a stop to its order yet, so {@link #whyNoMessages} waits for
 * one.
 *
 * <p>Built in M12. Rows: FR-378, FR-380.
 */
public final class OptOutQueries {

    private final PhoneHasher phoneHasher;
    private final OptOutList optOuts;
    private final MessagingViews views;
    private final RecipientContacts recipientContacts;
    private final PublishedRoutes publishedRoutes;

    /**
     * Creates the queries.
     *
     * @param phoneHasher the keyed phone hash
     * @param optOuts the phones' opt-out history
     * @param views Messaging's read port, for the settings
     * @param recipientContacts Customers &amp; Orders' recipient phones
     * @param publishedRoutes Planning's routes, for a stop's order
     */
    public OptOutQueries(
            PhoneHasher phoneHasher,
            OptOutList optOuts,
            MessagingViews views,
            RecipientContacts recipientContacts,
            PublishedRoutes publishedRoutes) {
        this.phoneHasher = phoneHasher;
        this.optOuts = optOuts;
        this.views = views;
        this.recipientContacts = recipientContacts;
        this.publishedRoutes = publishedRoutes;
    }

    /**
     * A phone's opt-out standing.
     *
     * <p>Built in M12.
     *
     * @param actor the dispatcher
     * @param phone the phone, as shown on the customer or the order
     * @return whether it may be messaged, and the latest entry of its history
     */
    public OptOutStandingView standing(Actor actor, PhoneNumber phone) {
        throw new UnsupportedOperationException("Built in M12");
    }

    /**
     * Why a stop's recipient gets no messages.
     *
     * <p>Built in M12.
     *
     * @param actor the dispatcher
     * @param stop the stop
     * @return the first reason that applies, or that messages may go
     * @throws NotFound when no plan holds the stop, or its order is gone
     */
    public WhyNoMessagesView whyNoMessages(Actor actor, StopId stop) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
