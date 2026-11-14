package com.dispatchflow.shared;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The refusals of the shared kernel's values (DL §3.3). Their fields carry no personal data:
 * neither a refused phone number nor refused coordinates appear in them.
 */
public sealed interface SharedKernelError extends DomainError {

    /**
     * A local time in the hour the clock skips in {@code Asia/Jerusalem} (BR-111).
     *
     * @param localTime the refused local date-time
     */
    record NonexistentLocalTime(LocalDateTime localTime) implements SharedKernelError {}

    /**
     * Elapsed seconds that fall outside their date's 23, 24 or 25 hours (DL §10.1, ADR-088).
     *
     * @param date the date the seconds count from
     * @param seconds the refused count
     */
    record OutsideTheDay(DeliveryDate date, long seconds) implements SharedKernelError {}

    /**
     * A time window whose start is not before its end, or whose ends lie on different dates
     * (BR-35).
     *
     * @param start the refused start
     * @param end the refused end
     */
    record InvalidTimeWindow(IsraelDateTime start, IsraelDateTime end) implements SharedKernelError {}

    /**
     * A latitude outside -90 to 90 or a longitude outside -180 to 180, or one that is not a number.
     * It names no coordinate, because a customer's location is personal data.
     */
    record InvalidCoordinates() implements SharedKernelError {}

    /** The quantity a {@link NonPositiveQuantity} or a {@link QuantityOutOfRange} refers to. */
    enum Measure {
        /** A {@link Weight}, in kilograms. */
        WEIGHT,
        /** A {@link Volume}, in litres. */
        VOLUME
    }

    /**
     * A weight or a volume that is zero or negative (BR-50).
     *
     * @param measure which quantity was refused
     */
    record NonPositiveQuantity(Measure measure) implements SharedKernelError {}

    /**
     * A weight or a volume with more than two decimals, or more integer digits than its column
     * holds: 6 for a weight and 7 for a volume (ADR-061, {@code numeric(8,2)} and {@code
     * numeric(9,2)}).
     *
     * @param measure which quantity was refused
     */
    record QuantityOutOfRange(Measure measure) implements SharedKernelError {}

    /**
     * A priority outside 1 to 5 (BR-53).
     *
     * @param priority the refused level
     */
    record PriorityOutOfRange(int priority) implements SharedKernelError {}

    /**
     * Text that is not a valid phone number (FR-377). It names no number, because a phone number
     * is personal data.
     */
    record InvalidPhoneNumber() implements SharedKernelError {}

    /**
     * A delivery date outside the years 1 to 9999. It is a technical bound, not a business rule:
     * the date arithmetic past it would fail.
     *
     * @param date the refused date
     */
    record DateOutOfRange(LocalDate date) implements SharedKernelError {}
}
