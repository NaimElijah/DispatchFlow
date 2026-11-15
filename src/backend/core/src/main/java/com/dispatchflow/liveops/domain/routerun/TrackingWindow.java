package com.dispatchflow.liveops.domain.routerun;

import com.dispatchflow.shared.IsraelDateTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * When a route run keeps positions: from its start until an hour after its current driver's shift
 * ends, or until it finishes, whichever is earlier (FR-328, NFR-30). The start and the finish are
 * included, and the cut-off is not: collection stops at it. The shift end is the one in force when
 * a position is checked, so after a hand-over the new driver's shift sets it, and a confirmed return
 * time past the shift replaces it (FR-394, ADR-107). Built at each check, never stored.
 *
 * @param startedAt when the run started
 * @param finishedAt when it finished, if it has
 * @param cutoff an hour after the current driver's shift end
 */
public record TrackingWindow(Instant startedAt, Optional<Instant> finishedAt, Instant cutoff) {

    /** How long after the shift's end positions are still kept on a route nobody finished (FR-328). */
    public static final Duration AFTER_SHIFT_END = Duration.ofHours(1);

    /** Checks every part is present. */
    public TrackingWindow {
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(finishedAt, "finishedAt");
        Objects.requireNonNull(cutoff, "cutoff");
    }

    /**
     * The window of a run, for the driver who drives it now.
     *
     * @param startedAt when the run started
     * @param finishedAt when it finished, if it has
     * @param shiftEnd the current driver's shift end that day, or the confirmed return time of
     *     FR-394
     * @return the window
     */
    public static TrackingWindow of(Instant startedAt, Optional<Instant> finishedAt, IsraelDateTime shiftEnd) {
        Objects.requireNonNull(shiftEnd, "shiftEnd");
        return new TrackingWindow(startedAt, finishedAt, shiftEnd.toInstant().plus(AFTER_SHIFT_END));
    }

    /**
     * Whether a position at an instant falls inside the window.
     *
     * @param at the position's time
     * @return {@code true} from the start, included, to the cut-off, excluded, and not after the
     *     finish
     */
    public boolean admits(Instant at) {
        return !at.isBefore(startedAt)
                && at.isBefore(cutoff)
                && finishedAt.map(finish -> !at.isAfter(finish)).orElse(true);
    }
}
