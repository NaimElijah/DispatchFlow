package com.dispatchflow.liveops.domain;

import com.dispatchflow.shared.StoredStateInvalid;
import java.time.Instant;
import java.util.Objects;

/**
 * The span in which a contact from the reporting account holds a late report (BR-132, BR-133). The
 * use case asks Access's {@code SessionContacts} about it; both ends are included (ADR-107).
 * {@link CorrectedTime#contactWindow()} builds it.
 *
 * @param from the first instant of the window
 * @param to the last instant of the window, not before {@code from}
 */
public record ContactWindow(Instant from, Instant to) {

    /**
     * Checks the ends.
     *
     * @throws StoredStateInvalid when {@code to} is before {@code from}, which only damaged state can
     *     give, because {@link CorrectedTime#contactWindow()} returns no window then
     */
    public ContactWindow {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (to.isBefore(from)) {
            throw new StoredStateInvalid(ContactWindow.class, "a contact window ends no earlier than it starts");
        }
    }

    /**
     * Whether an instant falls in the window, both ends included.
     *
     * @param instant the instant
     * @return {@code true} when it is at or after {@code from} and at or before {@code to}
     */
    public boolean contains(Instant instant) {
        return !instant.isBefore(from) && !instant.isAfter(to);
    }
}
