package com.dispatchflow.messaging.application.service;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.api.RaiseAlert;
import com.dispatchflow.messaging.events.RatingReceived;
import com.dispatchflow.shared.application.NotFound;

/**
 * Messaging's own listener on {@code RatingReceived}: a low rating alerts the dispatcher, as an
 * at-risk stop does, through Live Operations' {@link RaiseAlert} (FR-361, DL §7.2). A higher
 * rating does nothing.
 *
 * <p>Whether a rating is low is FR-361's domain rule, so it asks {@code Rating.isLow()} of the
 * event's rating, never a threshold of its own. The Messaging domain has no such method yet, which
 * is a domain gap. It changes no Messaging aggregate, so it writes no audit record of its own; Live
 * Operations audits the alert.
 *
 * <p>Delivery is at least once (ADR-064); the alert is raised once per rating request (ADR-107
 * point 4), so it records no {@code ProcessedEvents} pair.
 *
 * <p>It opens no transaction of its own: Live Operations' {@link RaiseAlert} is a use case that
 * opens its own, and pushes the alert only after that commit (ADR-129).
 *
 * <p>To Be Implemented. Rows: FR-361.
 */
public final class AlertLowRating {

    private final RaiseAlert raiseAlert;

    /**
     * Creates the listener's use case.
     *
     * @param raiseAlert Live Operations' alerts
     */
    public AlertLowRating(RaiseAlert raiseAlert) {
        this.raiseAlert = raiseAlert;
    }

    /**
     * Raises the low-rating alert when {@code Rating.isLow()} says the rating is low.
     *
     * <p>To Be Implemented.
     *
     * @param event the rating
     * @param call the listener's server step, which Live Operations' call carries
     * @throws NotFound when no plan holds the rated stop, so the delivery is retried
     */
    public void on(RatingReceived event, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
