package com.dispatchflow.liveops.application.api;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.RatingRequestId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.application.NotFound;

/**
 * Raises a dispatcher's alert for a fact another module heard (DL §2.2): today a recipient's low
 * rating, which Messaging's listener on {@code RatingReceived} reports (FR-361).
 *
 * <p>To Be Implemented.
 */
public interface RaiseAlert {

    /**
     * Raises a low-rating alert for a stop. Live Operations finds the stop's plan itself, and builds
     * its {@code AlertSubject.LowRating} from it. The alert is raised once per rating request, so a
     * retried listener is safe (ADR-107 point 4).
     *
     * <p>It takes no rating: the caller has already judged it low ({@code Rating.isLow()}, FR-361),
     * and {@code AlertSubject.LowRating} names only the plan, the stop and the request. The rating
     * itself stays Messaging's, on its ratings screen (FR-360).
     *
     * <p>To Be Implemented.
     *
     * @param stop the rated stop
     * @param request the rating request that was answered with a low rating
     * @param call the listener's server step
     * @throws NotFound when no plan holds the stop
     */
    void lowRating(StopId stop, RatingRequestId request, CallContext call);
}
