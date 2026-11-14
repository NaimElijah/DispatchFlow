package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.DomainError;
import java.util.List;
import java.util.Objects;

/**
 * An import row as the preview shows it: the row, and every error it had when last checked
 * (FR-203).
 *
 * @param row the row as read
 * @param errors its errors, in the order found; empty for a row that imports
 */
public record PreviewRow(ImportRow row, List<DomainError> errors) {

    /** Copies the errors. */
    public PreviewRow {
        Objects.requireNonNull(row, "row");
        errors = List.copyOf(errors);
    }
}
