package com.dispatchflow.messaging.application.service;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.liveops.application.api.RaiseAlert;
import com.dispatchflow.messaging.events.RatingReceived;
import com.dispatchflow.shared.application.NotFound;

/**
 * Messaging's own listener on {@code RatingReceived}: a rating from 1 to 4 alerts the dispatcher, as
 * an at-risk stop does, through Live Operations' {@link RaiseAlert} (FR-361, DL §7.2). A higher
 * rating does nothing. It changes no Messaging aggregate, so it writes no audit record of its own;
 * Live Operations audits the alert.
 *
 * <p>Delivery is at least once (ADR-064); the alert is raised once per rating request (ADR-107
 * point 4), so it records no {@code ProcessedEvents} pair.
 *
 * <p>Built in M12. Rows: FR-361.
 */
public final class AlertLowRating {

    /** The highest rating that alerts the dispatcher (FR-361). */
    public static final int HIGHEST_LOW_RATING = 4;

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
     * Raises the low-rating alert for a rating of 4 or less.
     *
     * <p>Built in M12.
     *
     * @param event the rating
     * @param call the listener's server step, which Live Operations' call carries
     * @throws NotFound when no plan holds the rated stop, so the delivery is retried
     */
    public void on(RatingReceived event, CallContext call) {
        throw new UnsupportedOperationException("Built in M12");
    }
}
