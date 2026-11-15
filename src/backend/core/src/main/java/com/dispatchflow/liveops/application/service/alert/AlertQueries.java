package com.dispatchflow.liveops.application.service.alert;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.liveops.application.port.out.HeldReportsView;
import com.dispatchflow.liveops.application.port.out.LiveOpsViews;
import com.dispatchflow.liveops.application.port.out.OpenAlertsView;
import com.dispatchflow.planning.application.api.PublishedRoutes;
import com.dispatchflow.shared.DeliveryDate;

/**
 * The dispatcher's alert list and the held reports waiting for a decision (FR-157, FR-261, FR-335,
 * FR-405, BR-132). The date's plan and stops come from Planning's {@link PublishedRoutes}, the
 * alerts and reports from {@link LiveOpsViews}. The phone app's Admin and Dispatcher screens read
 * the same list (FR-405).
 *
 * <p>Built in M8. Rows: FR-157, FR-261, FR-335, FR-405, BR-132.
 */
public final class AlertQueries {

    private final LiveOpsViews views;
    private final PublishedRoutes publishedRoutes;

    /**
     * Creates the queries.
     *
     * @param views Live Operations' read port
     * @param publishedRoutes Planning's routes in force, for a date's plan and stops
     */
    public AlertQueries(LiveOpsViews views, PublishedRoutes publishedRoutes) {
        this.views = views;
        this.publishedRoutes = publishedRoutes;
    }

    /**
     * A date's unacknowledged alerts: until the plan completes every one, and after it the ones
     * raised since (FR-261).
     *
     * <p>Built in M8.
     *
     * @param actor the dispatcher or Admin
     * @param date the date
     * @return the alerts; none when the date has no plan in force
     */
    public OpenAlertsView open(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M8");
    }

    /**
     * A date's reports held for a decision (BR-132).
     *
     * <p>Built in M8.
     *
     * @param actor the dispatcher
     * @param date the date
     * @return the open held reports; none when the date has no plan in force
     */
    public HeldReportsView held(Actor actor, DeliveryDate date) {
        throw new UnsupportedOperationException("Built in M8");
    }
}
