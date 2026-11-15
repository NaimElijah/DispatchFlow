package com.dispatchflow.liveops.domain.routerun;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.StopId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Where a route run stands (FR-244, DL §4.5). A route with no run is *not started*; a run exists
 * only once it is started (DL §3.5).
 */
public sealed interface RunStatus {

    /** The route is in progress. */
    record Started() implements RunStatus {}

    /**
     * The driver finished the route (FR-68), perhaps with stops pending (FR-329).
     *
     * @param at when, the finish report's corrected time
     * @param by the driver
     * @param pending the stops with no outcome when it finished, in the route's order
     */
    record Finished(Instant at, AccountId by, List<StopId> pending) implements RunStatus {

        /** Checks every part is present, and copies the stops. */
        public Finished {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(by, "by");
            pending = List.copyOf(pending);
        }
    }
}
