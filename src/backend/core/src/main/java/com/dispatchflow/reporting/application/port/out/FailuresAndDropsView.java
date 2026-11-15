package com.dispatchflow.reporting.application.port.out;

import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.PersonalData;
import java.util.List;
import java.util.Objects;

/**
 * The failed and dropped orders of a range of dates, which the screen groups by reason, customer
 * and driver (FR-161). Its entries name customers, so it prints redacted; which of their fields
 * may be shown waits on legal advice (Q-42).
 *
 * <p>Built in an unscheduled milestone: FR-161, a Should, is in no milestone of the roadmap.
 *
 * @param from the first date, included
 * @param to the last date, included
 * @param entries every failed stop and dropped order of the range, by date and then by order
 */
public record FailuresAndDropsView(DeliveryDate from, DeliveryDate to, List<FailureOrDrop> entries)
        implements PersonalData {

    /** Checks the dates are present, and copies the entries. */
    public FailuresAndDropsView {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        entries = List.copyOf(entries);
    }

    /**
     * A form that hides the customers' names.
     *
     * @return {@code FailuresAndDropsView[redacted]}
     */
    @Override
    public String toString() {
        return "FailuresAndDropsView[redacted]";
    }
}
