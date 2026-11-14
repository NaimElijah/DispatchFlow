package com.dispatchflow.shared;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * The refusals of the shared kernel's values (DL §3.3). Their fields carry no personal data:
 * neither a refused phone number, refused coordinates nor refused free text appear in them.
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
     * A delivery date outside the years 1 to 9999. The bound is a chosen technical one, not a
     * business rule and not where date arithmetic fails.
     *
     * @param date the refused date
     */
    record DateOutOfRange(LocalDate date) implements SharedKernelError {}

    /**
     * Free text longer than its kind's cap, counted in code points after normalization, or raw
     * input more than eight UTF-16 units per code point of the cap, which is refused before it is
     * normalized (ADR-106). It names no text, because a name, an address or a note is personal
     * data.
     *
     * @param kind the kind of text refused
     * @param maxLength the most code points that kind holds
     */
    record TextTooLong(TextKind kind, int maxLength) implements SharedKernelError {}

    /**
     * Free text holding a character its kind refuses (ADR-106): a control character other than a
     * line feed in a note; a line or paragraph separator outside a note; an interior format
     * character in a name or an address, other than U+061C and U+200C to U+200F; a bidi
     * embedding, override or isolate control; an unpaired surrogate; or a private-use or unassigned code point. It
     * names no text or character, because the text is personal data.
     *
     * @param kind the kind of text refused
     */
    record TextHasForbiddenCharacters(TextKind kind) implements SharedKernelError {}
}
