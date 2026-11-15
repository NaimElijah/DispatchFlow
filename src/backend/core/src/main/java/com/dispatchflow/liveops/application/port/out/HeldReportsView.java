package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.delivery.DeliverySnapshot.HeldReport;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import java.util.List;
import java.util.Objects;

/**
 * The reports a date's stops hold for a dispatcher's decision (BR-132, BR-135, FR-157). Each states
 * an outcome with its reason or proof, so it prints redacted.
 *
 * <p>Built in M8.
 *
 * @param date the date
 * @param reports the open held reports, in the order they were held
 */
public record HeldReportsView(DeliveryDate date, List<HeldEntry> reports) implements PersonalData {

    /** Checks the date is present, and copies the reports. */
    public HeldReportsView {
        Objects.requireNonNull(date, "date");
        reports = List.copyOf(reports);
    }

    /**
     * A form that leaves the reports out.
     *
     * @return the date and the number of reports
     */
    @Override
    public String toString() {
        return "HeldReportsView[date=" + date + ", reports=" + reports.size() + ", redacted]";
    }

    /**
     * One open held report.
     *
     * <p>Built in M8.
     *
     * @param stop the stop it is held on
     * @param report the report: what it states, who sent it from which route, when, and the
     *     cancellation it would override
     * @param version the stop's delivery version, which confirming or refusing sends back (BR-76)
     */
    public record HeldEntry(StopId stop, HeldReport report, Version version) implements PersonalData {

        /** Checks every part is present. */
        public HeldEntry {
            Objects.requireNonNull(stop, "stop");
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
