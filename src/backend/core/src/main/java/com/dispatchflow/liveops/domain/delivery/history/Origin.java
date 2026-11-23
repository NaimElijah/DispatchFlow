package com.dispatchflow.liveops.domain.delivery.history;

import com.dispatchflow.liveops.domain.CancellationKind;
import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RouteId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** How an outcome came to be recorded. */
public sealed interface Origin extends PersonalData {

    /**
     * A driver's report, recorded at once (FR-29).
     *
     * @param route the route the report came from, which held the stop at its time (DL §8)
     * @param revision the revision that entitled it (BR-110)
     * @param time when it happened
     * @param servedAfter the cancellation it came before, when the stop's order or plan was
     *     cancelled or rescheduled after it: served after cancellation (BR-130, ADR-101)
     */
    record Reported(RouteId route, RevisionNo revision, CorrectedTime time, Optional<CancellationKind> servedAfter)
            implements Origin {

        /** Checks every part is present. */
        public Reported {
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(servedAfter, "servedAfter");
        }

        /**
         * A form with nothing personal to hide; declared because the type is marked.
         *
         * @return the origin
         */
        @Override
        public String toString() {
            return "Reported[route=" + route + ", revision=" + revision + ", time=" + time + ", servedAfter="
                    + servedAfter + "]";
        }
    }

    /**
     * A held report a dispatcher confirmed (BR-132).
     *
     * @param report the held report, which keeps its route, revision and time
     */
    record ConfirmedHeld(HeldReportId report) implements Origin {

        /** Checks the report is present. */
        public ConfirmedHeld {
            Objects.requireNonNull(report, "report");
        }

        /**
         * A form with nothing personal to hide; declared because the type is marked.
         *
         * @return the origin
         */
        @Override
        public String toString() {
            return "ConfirmedHeld[report=" + report + "]";
        }
    }

    /**
     * A dispatcher's record on the driver's behalf (FR-73, FR-257).
     *
     * @param route the route whose driver it was recorded for
     */
    record OnBehalf(RouteId route) implements Origin {

        /** Checks the route is present. */
        public OnBehalf {
            Objects.requireNonNull(route, "route");
        }

        /**
         * A form with nothing personal to hide; declared because the type is marked.
         *
         * @return the origin
         */
        @Override
        public String toString() {
            return "OnBehalf[route=" + route + "]";
        }
    }

    /**
     * A dispatcher's correction (FR-333).
     *
     * @param reason why
     * @param correctedAt when the correction was made
     */
    record Correction(CorrectionReason reason, Instant correctedAt) implements Origin {

        /** Checks both parts are present. */
        public Correction {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(correctedAt, "correctedAt");
        }

        /**
         * A form that hides the reason, because it is personal data.
         *
         * @return the origin, redacted
         */
        @Override
        public String toString() {
            return "Correction[reason=redacted, correctedAt=" + correctedAt + "]";
        }
    }
}
