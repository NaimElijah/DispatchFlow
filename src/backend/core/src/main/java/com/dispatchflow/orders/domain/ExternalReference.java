package com.dispatchflow.orders.domain;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * An order's reference in an outside system (FR-85), trimmed and compared exactly. It is unique
 * among a date's uncancelled orders (FR-211, ADR-102). Blank text is no reference, so a reference
 * always has text.
 */
public final class ExternalReference {

    private final String text;

    private ExternalReference(String text) {
        this.text = text;
    }

    /**
     * Reads a reference as typed or imported.
     *
     * @param text the reference
     * @return the trimmed reference, or empty when the text is empty or only whitespace
     */
    public static Optional<ExternalReference> of(String text) {
        String trimmed = Objects.requireNonNull(text, "text").strip();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(new ExternalReference(trimmed));
    }

    /**
     * The reference's text.
     *
     * @return the trimmed, non-empty text
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof ExternalReference that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * A form that hides the text: an outside system may build a reference from a customer's data.
     *
     * @return {@code ExternalReference[redacted]}
     */
    @Override
    public String toString() {
        return "ExternalReference[redacted]";
    }
}
