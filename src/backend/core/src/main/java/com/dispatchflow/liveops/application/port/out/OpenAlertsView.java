package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.alert.AlertSubject;
import com.dispatchflow.shared.AlertId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The dispatcher's one alert list for a date (FR-157): every unacknowledged alert of the date's plan
 * until the plan completes, and one raised after that until it is acknowledged (FR-261). An alert
 * raised while the web app was closed waits here (FR-335).
 *
 * <p>Built in M8.
 *
 * @param date the date
 * @param alerts the alerts, the newest first
 */
public record OpenAlertsView(DeliveryDate date, List<OpenAlert> alerts) {

    /** Checks the date is present, and copies the alerts. */
    public OpenAlertsView {
        Objects.requireNonNull(date, "date");
        alerts = List.copyOf(alerts);
    }

    /**
     * One unacknowledged alert.
     *
     * <p>Built in M8.
     *
     * @param alert the alert
     * @param subject what it is about: its kind and the records it names
     * @param raisedAt when it was raised
     * @param version its version, which acknowledging it sends back (BR-76)
     */
    public record OpenAlert(AlertId alert, AlertSubject subject, Instant raisedAt, Version version) {

        /** Checks every part is present. */
        public OpenAlert {
            Objects.requireNonNull(alert, "alert");
            Objects.requireNonNull(subject, "subject");
            Objects.requireNonNull(raisedAt, "raisedAt");
            Objects.requireNonNull(version, "version");
        }
    }
}
