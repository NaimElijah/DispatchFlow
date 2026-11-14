package com.dispatchflow.orders.domain;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * An order's apartment (FR-86), as typed. Blank text is no apartment, so an apartment always has
 * text. The driver sees it at the stop.
 *
 * <p>It is a class with one factory rather than a record, so a blank one can't be built. It is
 * personal data with the order's address: {@link #toString()} hides it. It takes the kernel's
 * shared text rule once that rule exists.
 */
public final class Apartment {

    private final String text;

    private Apartment(String text) {
        this.text = text;
    }

    /**
     * Reads the apartment as typed or imported.
     *
     * @param text the apartment
     * @return the trimmed apartment, or empty when the text is empty or only whitespace
     */
    public static Optional<Apartment> of(String text) {
        String trimmed = Objects.requireNonNull(text, "text").strip();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(new Apartment(trimmed));
    }

    /**
     * The apartment's text.
     *
     * @return the trimmed, non-empty text
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof Apartment that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * A form that hides the text, because it is personal data.
     *
     * @return {@code Apartment[redacted]}
     */
    @Override
    public String toString() {
        return "Apartment[redacted]";
    }
}
