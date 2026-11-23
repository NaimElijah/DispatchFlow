package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.liveops.domain.CancellationKind;
import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.liveops.domain.outcome.Outcome;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Version;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A delivery's stored state: what {@link Delivery#snapshot()} returns and {@link Delivery#restore}
 * rebuilds from. Its records hold reasons, proofs and notes, so it is personal data, and {@link
 * #toString()} leaves them out.
 *
 * @param stop the stop, which keys the delivery
 * @param order the stop's order
 * @param attempt the stop's attempt
 * @param version the version it was loaded at, or null for an aggregate never saved
 *     (ADR-126)
 * @param arrival the driver's arrival, if any (FR-29)
 * @param outcomes the original outcome first, then each correction in order (FR-333)
 * @param heldReports every report held for a dispatcher, in the order held (BR-132)
 */
public record DeliverySnapshot(
        StopId stop,
        OrderId order,
        Attempt attempt,
        @Nullable Version version,
        Optional<ArrivalRecord> arrival,
        List<OutcomeRecord> outcomes,
        List<HeldReport> heldReports)
        implements PersonalData {

    /** Checks every field is present, and copies the lists. */
    public DeliverySnapshot {
        Objects.requireNonNull(stop, "stop");
        Objects.requireNonNull(order, "order");
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(arrival, "arrival");
        outcomes = List.copyOf(outcomes);
        heldReports = List.copyOf(heldReports);
    }

    /**
     * A form that names the stop and counts its records, and leaves their content out.
     *
     * @return the snapshot, redacted
     */
    @Override
    public String toString() {
        return "DeliverySnapshot[stop=" + stop + ", order=" + order + ", attempt=" + attempt + ", version="
                + version + ", arrival=" + arrival + ", outcomes=" + outcomes.size() + ", heldReports="
                + heldReports.size() + "]";
    }

    /**
     * A driver's arrival at the stop (FR-29, BR-87).
     *
     * @param driver the driver
     * @param route the route the report came from
     * @param time when it happened
     */
    public record ArrivalRecord(AccountId driver, RouteId route, CorrectedTime time) {

        /** Checks every part is present. */
        public ArrivalRecord {
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(time, "time");
        }
    }

    /**
     * One outcome on record: the original or a correction, which never replaces it (FR-333).
     *
     * @param outcome what it says
     * @param by the driver who reported it, or the dispatcher who recorded or corrected it
     * @param factTime the time the outcome counts from: the report's corrected time, the time a
     *     dispatcher recorded it, or for a correction the time it states or else the original's
     *     (ADR-107)
     * @param origin how it was recorded
     */
    public record OutcomeRecord(Outcome outcome, AccountId by, Instant factTime, Origin origin)
            implements PersonalData {

        /** Checks every part is present. */
        public OutcomeRecord {
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(by, "by");
            Objects.requireNonNull(factTime, "factTime");
            Objects.requireNonNull(origin, "origin");
        }

        /**
         * A form that leaves out the outcome's and the origin's personal content.
         *
         * @return the record, redacted
         */
        @Override
        public String toString() {
            return "OutcomeRecord[outcome=" + outcome + ", by=" + by + ", factTime=" + factTime + ", origin=" + origin
                    + "]";
        }
    }

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

    /**
     * A driver's report held for a dispatcher's decision (BR-132, BR-136).
     *
     * @param id its local ID
     * @param outcome what the report states
     * @param driver the reporting driver
     * @param route the route the report came from
     * @param revision the revision that entitled it
     * @param time when it happened
     * @param overrides the cancellation it would override, if any (BR-132)
     * @param state open, confirmed or refused
     */
    public record HeldReport(
            HeldReportId id,
            Outcome outcome,
            AccountId driver,
            RouteId route,
            RevisionNo revision,
            CorrectedTime time,
            Optional<CancellationKind> overrides,
            HeldState state)
            implements PersonalData {

        /** Checks every part is present. */
        public HeldReport {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(driver, "driver");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(time, "time");
            Objects.requireNonNull(overrides, "overrides");
            Objects.requireNonNull(state, "state");
        }

        /**
         * Whether the report still waits for a decision.
         *
         * @return {@code true} while it is open
         */
        public boolean isOpen() {
            return state instanceof HeldState.Open;
        }

        /**
         * This report in another state.
         *
         * @param next the new state
         * @return the report
         */
        HeldReport in(HeldState next) {
            return new HeldReport(id, outcome, driver, route, revision, time, overrides, next);
        }

        /**
         * A form that leaves out the outcome's personal content.
         *
         * @return the report, redacted
         */
        @Override
        public String toString() {
            return "HeldReport[id=" + id + ", outcome=" + outcome + ", driver=" + driver + ", route=" + route
                    + ", revision=" + revision + ", time=" + time + ", overrides=" + overrides + ", state="
                    + state + "]";
        }
    }

    /** Where a held report stands (DL §9.3, §9.7). */
    public sealed interface HeldState {

        /** Waiting for a dispatcher. */
        record Open() implements HeldState {}

        /**
         * A dispatcher confirmed it, and it became the stop's outcome.
         *
         * @param by the dispatcher
         * @param at when
         */
        record Confirmed(AccountId by, Instant at) implements HeldState {

            /** Checks both parts are present. */
            public Confirmed {
                Objects.requireNonNull(by, "by");
                Objects.requireNonNull(at, "at");
            }
        }

        /**
         * A dispatcher refused it, as BR-90 refuses a contradicting report (BR-135).
         *
         * @param by the dispatcher
         * @param at when
         */
        record RefusedByDispatcher(AccountId by, Instant at) implements HeldState {

            /** Checks both parts are present. */
            public RefusedByDispatcher {
                Objects.requireNonNull(by, "by");
                Objects.requireNonNull(at, "at");
            }
        }

        /**
         * Another outcome became the stop's first, so this one was refused as BR-90 says: one on the
         * stop's current route, or another held report confirmed (BR-134, BR-18).
         *
         * @param at when
         */
        record Overtaken(Instant at) implements HeldState {

            /** Checks the time is present. */
            public Overtaken {
                Objects.requireNonNull(at, "at");
            }
        }
    }
}
