package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.PersonalData;
import java.util.List;
import java.util.Objects;

/**
 * An import row as the preview shows it: the row, and every error it had when last checked
 * (FR-203).
 *
 * @param row the row as read
 * @param errors its errors, in the order found; empty for a row that imports
 */
public record PreviewRow(ImportRow row, List<DomainError> errors) implements PersonalData {

    /** Copies the errors. */
    public PreviewRow {
        Objects.requireNonNull(row, "row");
        errors = List.copyOf(errors);
    }

    /**
     * A form that hides the row, because it holds personal data.
     *
     * @return {@code PreviewRow[redacted]}
     */
    @Override
    public String toString() {
        return "PreviewRow[redacted]";
    }
}
