package com.dispatchflow.orders.domain;

import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The code that opens an order's building (FR-86). Blank text is no entrance code, so an entrance
 * code always has text. The driver sees it at the stop.
 *
 * <p>It is a class with one factory rather than a record, so a blank one can't be built. It is
 * personal data with the order's address: {@link #toString()} hides it. It takes the kernel's
 * shared text rule once that rule exists.
 */
public final class EntranceCode {

    private final String text;

    private EntranceCode(String text) {
        this.text = text;
    }

    /**
     * Reads the entrance code as typed or imported.
     *
     * @param text the entrance code
     * @return the trimmed entrance code, or empty when the text is empty or only whitespace
     */
    public static Optional<EntranceCode> of(String text) {
        String trimmed = Objects.requireNonNull(text, "text").strip();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(new EntranceCode(trimmed));
    }

    /**
     * The entrance code's text.
     *
     * @return the trimmed, non-empty text
     */
    public String text() {
        return text;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        return other instanceof EntranceCode that && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    /**
     * A form that hides the text, because it is personal data.
     *
     * @return {@code EntranceCode[redacted]}
     */
    @Override
    public String toString() {
        return "EntranceCode[redacted]";
    }
}
