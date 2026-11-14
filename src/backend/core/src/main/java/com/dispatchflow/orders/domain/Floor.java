package com.dispatchflow.orders.domain;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The floor of an order's door (FR-86), as typed: a number, or a word such as a ground floor's
 * name. Blank text is no floor, so a floor always has text. The driver sees it at the stop.
 *
 * <p>It is a class with one factory rather than a record, so a blank one can't be built. It is
 * personal data with the order's address: {@link #toString()} hides it. It takes the kernel's
 * shared text rule once that rule exists.
 */
public final class Floor {

    private final String text;

    private Floor(String text) {
        this.text = text;
    }

    /**
     * Reads the floor as typed or imported.
     *
     * @param text the floor
     * @return the trimmed floor, or empty when the text is empty or only whitespace
     */
    public static Optional<Floor> of(String text) {
        String trimmed = Objects.requireNonNull(text, "text").strip();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(new Floor(trimmed));
    }

    /**
     * The floor's text.
     *
     * @return the trimmed, non-empty text
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Floor that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * A form that hides the text, because it is personal data.
     *
     * @return {@code Floor[redacted]}
     */
    @Override
    public String toString() {
        return "Floor[redacted]";
    }
}
