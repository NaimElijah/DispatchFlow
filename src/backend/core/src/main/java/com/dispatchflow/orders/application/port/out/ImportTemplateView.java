package com.dispatchflow.orders.application.port.out;

import java.util.List;

/**
 * The order import's template: the fixed, named columns a file must have, in file order (FR-84,
 * FR-309). The web adapter writes it as the file the dispatcher downloads.
 *
 * <p>Built in an unscheduled milestone.
 *
 * @param columns the column names, each once, in file order
 */
public record ImportTemplateView(List<String> columns) {

    /** Copies the columns. */
    public ImportTemplateView {
        columns = List.copyOf(columns);
    }
}
