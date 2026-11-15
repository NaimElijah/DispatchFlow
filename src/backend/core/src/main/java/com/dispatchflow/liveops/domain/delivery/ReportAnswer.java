package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.liveops.domain.HeldReportId;
import java.util.List;
import java.util.Objects;

/**
 * What a recorded fact did, for the use case to answer the phone and raise the dispatcher's alerts
 * (DL §6.2, §8): the outcome was recorded, perhaps overtaking held reports, or the report is held.
 */
public sealed interface ReportAnswer {

    /**
     * The outcome was recorded.
     *
     * @param overtaken the held reports it refused, which are shown to the dispatcher (BR-134,
     *     BR-90), in the order they were held
     */
    record Recorded(List<HeldReportId> overtaken) implements ReportAnswer {

        /** Copies the list. */
        public Recorded {
            overtaken = List.copyOf(overtaken);
        }
    }

    /**
     * The report is held for a dispatcher's decision; the phone's outbox is told it was received
     * (BR-132, FR-248).
     *
     * @param report the held report
     */
    record Held(HeldReportId report) implements ReportAnswer {

        /** Checks the report is present. */
        public Held {
            Objects.requireNonNull(report, "report");
        }
    }
}
