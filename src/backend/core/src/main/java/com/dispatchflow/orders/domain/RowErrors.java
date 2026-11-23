package com.dispatchflow.orders.domain;

import com.dispatchflow.shared.DomainError;
import java.util.List;

/**
 * The errors of one import row, as its stored row keeps them: the errors the preview found, or the
 * fields the reader refused (FR-203). They are the one value stored as JSON text, in one column,
 * because each error is a record of its own type from any module's errors (ADR-127 decision 7);
 * the persistence adapter's converter writes and reads that text.
 *
 * @param errors the errors, in the order found
 */
public record RowErrors(List<DomainError> errors) {

    /** No error, the errors of a row that imports. */
    public static final RowErrors NONE = new RowErrors(List.of());

    /** Copies the errors. */
    public RowErrors {
        errors = List.copyOf(errors);
    }
}
