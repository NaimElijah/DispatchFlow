package com.dispatchflow.messaging.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.messaging.application.port.out.MessagingViews;
import com.dispatchflow.messaging.application.port.out.RatingsView;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.DeliveryDate;
import org.springframework.transaction.annotation.Transactional;

/**
 * The ratings screen: a date's ratings and the comments kept on each delivery, which Admins and
 * Dispatchers read (FR-360). It gives no per-route or per-driver figure until Q-42 is answered
 * (FR-381). The date's stops come from Planning's {@link PublishedRoutes}.
 *
 * <p>Built in M12. Rows: FR-360, FR-381.
 */
@Transactional(readOnly = true)
public class RatingQueries {

    private final MessagingViews views;
    private final PublishedRoutes publishedRoutes;

    /**
     * Creates the queries.
     *
     * @param views Messaging's read port
     * @param publishedRoutes Planning's routes in force, for a date's stops
     */
    public RatingQueries(MessagingViews views, PublishedRoutes publishedRoutes) {
        this.views = views;
        this.publishedRoutes = publishedRoutes;
    }

    /**
     * A date's ratings and comments.
     *
     * <p>Built in M12.
     *
     * @param actor the Admin or dispatcher
     * @param date the date
     * @return the stops that had a rating request; none when the date has no plan in force
     */
    public RatingsView ofDate(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
