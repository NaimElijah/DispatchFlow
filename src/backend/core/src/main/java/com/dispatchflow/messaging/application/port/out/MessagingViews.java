package com.dispatchflow.messaging.application.port.out;

import com.dispatchflow.shared.StopId;
import java.util.List;
import java.util.Optional;

/**
 * Recipient Messaging's read port: what its own schema answers for the screens, read without loading
 * the aggregates (DL §13). The stops of a date come from Planning's {@code PublishedRoutes}; the
 * query classes join them with these rows.
 *
 * <p>To Be Implemented.
 */
public interface MessagingViews {

    /**
     * The rating requests of several stops (FR-360).
     *
     * <p>To Be Implemented.
     *
     * @param stops the stops, each once
     * @return one row per stop that had a rating request; the others are left out
     */
    List<RatingsView.RatedStop> ratingsOf(List<StopId> stops);

    /**
     * The messaging settings as the settings page shows them.
     *
     * <p>To Be Implemented.
     *
     * @return the settings, or empty before an Admin first set them
     */
    Optional<MessagingSettingsView> settings();
}
