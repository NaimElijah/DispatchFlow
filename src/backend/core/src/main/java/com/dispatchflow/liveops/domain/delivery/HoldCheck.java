package com.dispatchflow.liveops.domain.delivery;

import com.dispatchflow.liveops.domain.ContactWindow;
import com.dispatchflow.liveops.domain.CorrectedTime;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The facts that decide whether a driver's late outcome is held for a dispatcher (DL §3.3,
 * BR-132, BR-133): where the stop is now, from Planning's {@code RouteMembership}, and the contact
 * times Access's {@code SessionContacts} answered for the reporting account, across all its
 * sessions. It does not cover a stop that already has a held report: {@link Delivery} holds a
 * further report for it without asking, and refuses one from the same driver and route (BR-136).
 *
 * @param where where the stop is now
 * @param contacts the account's contact times around the report's {@link ContactWindow}; ones
 *     outside the window are ignored
 */
public record HoldCheck(StopWhereabouts where, List<Instant> contacts) {

    /** Checks both parts are present, and copies the times. */
    public HoldCheck {
        Objects.requireNonNull(where, "where");
        contacts = List.copyOf(contacts);
    }

    /**
     * Creates the check.
     *
     * @param where where the stop is now
     * @param contacts the account's contact times
     * @return the check
     */
    public static HoldCheck of(StopWhereabouts where, List<Instant> contacts) {
        return new HoldCheck(where, contacts);
    }

    /**
     * Whether the report is held: it would take the stop off another driver's route or override a
     * cancellation, and the account made contact within its window (BR-132, BR-133).
     *
     * @param time the report's corrected time
     * @return {@code true} when the report is held
     */
    public boolean holds(CorrectedTime time) {
        boolean movesOrOverrides = where instanceof StopWhereabouts.OnAnotherRoute
                || where.cancelledAfter(time).isPresent();
        return movesOrOverrides
                && time.contactWindow()
                        .map(window -> contacts.stream().anyMatch(window::contains))
                        .orElse(false);
    }
}
