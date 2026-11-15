package com.dispatchflow.liveops.application.port.out;

import com.dispatchflow.liveops.domain.HeldReportId;
import java.util.Objects;

/**
 * What a phone request did, as {@link ReportReceipts} stores it and a retry with the same key gets
 * back (BR-28, ADR-087). A refused request changes nothing and stores nothing, so its retry is
 * judged again.
 *
 * <p>Built in M8.
 */
public sealed interface RecordedReport {

    /**
     * The report's fact was recorded: an arrival or an outcome.
     *
     * <p>Built in M8.
     */
    record Recorded() implements RecordedReport {}

    /**
     * The report is held for a dispatcher's decision; the outbox is told it was received (BR-132,
     * FR-248).
     *
     * <p>Built in M8.
     *
     * @param report the held report
     */
    record Held(HeldReportId report) implements RecordedReport {

        /** Checks the report is present. */
        public Held {
            Objects.requireNonNull(report, "report");
        }
    }

    /**
     * A batch of positions was taken, each kept or refused by the tracking rule (NFR-285, FR-328).
     *
     * <p>Built in M8.
     */
    record PositionsTaken() implements RecordedReport {}
}
