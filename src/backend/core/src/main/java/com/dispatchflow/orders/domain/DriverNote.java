package com.dispatchflow.orders.domain;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A note the driver sees at the stop: a customer's standing notes (FR-08), or an order's own
 * delivery note (FR-135). Blank text is no note, so a note always has text.
 *
 * <p>A note can say anything about a person, so it is personal data: {@link #toString()} hides it,
 * and no error or event carries it.
 */
public final class DriverNote {

    private final String text;

    private DriverNote(String text) {
        this.text = text;
    }

    /**
     * Reads a note as typed or imported.
     *
     * @param text the note
     * @return the trimmed note, or empty when the text is empty or only whitespace
     */
    public static Optional<DriverNote> of(String text) {
        String trimmed = Objects.requireNonNull(text, "text").strip();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(new DriverNote(trimmed));
    }

    /**
     * The note's text.
     *
     * @return the trimmed, non-empty text
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof DriverNote that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * A form that hides the note, because it is personal data.
     *
     * @return {@code DriverNote[redacted]}
     */
    @Override
    public String toString() {
        return "DriverNote[redacted]";
    }
}
