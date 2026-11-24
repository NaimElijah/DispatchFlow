package com.dispatchflow.orders.application.service.importbatch;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.orders.application.port.out.ImportPreviewView;
import com.dispatchflow.orders.application.port.out.ImportTemplateView;
import com.dispatchflow.orders.application.port.out.OrdersViews;
import com.dispatchflow.shared.ImportBatchId;
import com.dispatchflow.shared.application.NotFound;
import org.springframework.transaction.annotation.Transactional;

/**
 * The order import screens' reads (FR-84, FR-203, ADR-086): a preview to check before confirming,
 * and the template file. Every read is the dispatcher's, authorized by role from its operation
 * (ADR-068); no driver scope applies.
 *
 * <p>Built in M2 and an unscheduled milestone, method by method.
 */
@Transactional(readOnly = true)
public class ImportQueries {

    private final OrdersViews views;

    /**
     * Creates the queries.
     *
     * @param views Customers &amp; Orders' read port
     */
    public ImportQueries(OrdersViews views) {
        this.views = views;
    }

    /**
     * A preview with every row and every error (FR-203, FR-204), its encoding (FR-313) and when it
     * expires (FR-399).
     *
     * <p>Built in M2.
     *
     * @param actor the dispatcher
     * @param batch the import
     * @return the preview
     * @throws NotFound when no import has the ID, or it is no longer previewed
     */
    public ImportPreviewView preview(Actor actor, ImportBatchId batch) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * The order import's template: its fixed, named columns (FR-84, FR-309).
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param actor the dispatcher
     * @return the template's columns
     */
    public ImportTemplateView template(Actor actor) {
        throw new UnsupportedOperationException("Built in an unscheduled milestone");
    }
}
