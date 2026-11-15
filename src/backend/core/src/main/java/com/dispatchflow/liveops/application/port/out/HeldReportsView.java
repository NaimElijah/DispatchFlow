package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.delivery.DeliverySnapshot.HeldReport;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Objects;

/**
 * The reports held for a dispatcher's decision, whatever their stops' dates: a held report is a
 * late one, so it still waits after its plan completes (BR-132, BR-135, FR-157). Each states an
 * outcome with its reason or proof, so it prints redacted.
 *
 * <p>Built in M8.
 *
 * @param reports the open held reports, in the order they were held
 */
public record HeldReportsView(List<HeldEntry> reports) implements PersonalData {

    /** Copies the reports. */
    public HeldReportsView {
        reports = List.copyOf(reports);
    }

    /**
     * A form that leaves the reports out.
     *
     * @return the number of reports
     */
    @Override
    public String toString() {
        return "HeldReportsView[reports=" + reports.size() + ", redacted]";
    }

    /**
     * One open held report.
     *
     * <p>Built in M8.
     *
     * @param stop the stop it is held on
     * @param date the delivery date of the stop's plan, so a report held past that date still shows
     *     the day it is for (BR-132)
     * @param report the report: what it states, who sent it from which route, when, and the
     *     cancellation it would override
     * @param version the stop's delivery version, which confirming or refusing sends back (BR-76)
     */
    public record HeldEntry(StopId stop, DeliveryDate date, HeldReport report, Version version)
            implements PersonalData {

        /** Checks every part is present. */
        public HeldEntry {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(date, "date");
            Objects.requireNonNull(report, "report");
            Objects.requireNonNull(version, "version");
        }

        /**
         * A form that leaves the report out.
         *
         * @return the stop, redacted
         */
        @Override
        public String toString() {
            return "HeldEntry[stop=" + stop + ", redacted]";
        }
    }
}
