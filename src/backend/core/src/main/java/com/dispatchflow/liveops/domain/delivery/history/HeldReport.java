package com.dispatchflow.liveops.domain.delivery.history;

import com.dispatchflow.liveops.domain.CancellationKind;
import com.dispatchflow.liveops.domain.CorrectedTime;
import com.dispatchflow.liveops.domain.HeldReportId;
import com.dispatchflow.liveops.domain.RevisionNo;
import com.dispatchflow.liveops.domain.outcome.Outcome;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RouteId;
import java.util.Objects;
import java.util.Optional;

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
     * A form that leaves out the outcome's personal content.
     *
     * @return the report, redacted
     */
    @Override
    public String toString() {
        return "HeldReport[id=" + id + ", outcome=" + outcome + ", driver=" + driver + ", route=" + route
                + ", revision=" + revision + ", time=" + time + ", overrides=" + overrides + ", state=" + state
                + "]";
    }
}
