package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.alert.AlertSubject;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The dispatcher's one alert list (FR-157), across dates: every unacknowledged alert of a plan not
 * yet completed, and one raised after its plan completed until it is acknowledged (FR-261). An alert
 * raised while the web app was closed waits here (FR-335).
 *
 * <p>Built in M8.
 *
 * @param alerts the alerts, the newest first
 */
public record OpenAlertsView(List<OpenAlert> alerts) {

    /** Copies the alerts. */
    public OpenAlertsView {
        alerts = List.copyOf(alerts);
    }

    /**
     * One unacknowledged alert.
     *
     * <p>Built in M8.
     *
     * @param alert the alert
     * @param subject what it is about: its kind and the records it names
     * @param date its plan's delivery date, which the list shows beside it, since it spans dates
     * @param raisedAt when it was raised
     * @param version its version, which acknowledging it sends back (BR-76)
     */
    public record OpenAlert(AlertId alert, AlertSubject subject, DeliveryDate date, Instant raisedAt, Version version) {

        /** Checks every part is present. */
        public OpenAlert {
            Objects.requireNonNull(alert, "alert");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(raisedAt, "raisedAt");
            Objects.requireNonNull(version, "version");
        }
    }
}
