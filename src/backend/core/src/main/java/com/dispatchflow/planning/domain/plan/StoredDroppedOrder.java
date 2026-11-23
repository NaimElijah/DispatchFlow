package com.dispatchflow.planning.domain.plan;

import com.dispatchflow.planning.domain.drop.DroppedOrder;
import com.dispatchflow.shared.Attempt;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.StoredStateInvalid;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Embedded;

/**
 * The columns one {@link DroppedOrder} is stored in, a row of its owner's drop table (ADR-127
 * decision 3): its order and attempt, and its reason in {@link StoredDropReason}'s kind and fact
 * columns. {@link #droppedOrder()} builds the public record every caller sees. A plan, a solve job's
 * result and a proposal store their drops this way; it is package-private, not nested, so that every
 * aggregate of this package can.
 */
@Embeddable
class StoredDroppedOrder {

    // PostgreSQL reserves "order".
    @Column(name = "order_id")
    private OrderId order;

    private Attempt attempt;

    @Embedded
    private StoredDropReason reason;

    /** For Hibernate, which sets the fields from the stored row. */
    protected StoredDroppedOrder() {}

    /**
     * Stores a drop.
     *
     * @param drop the drop
     */
    StoredDroppedOrder(DroppedOrder drop) {
        this.order = drop.order();
        this.attempt = drop.attempt();
        this.reason = new StoredDropReason(drop.reason());
    }

    /**
     * The drop these columns store.
     *
     * @return the drop
     * @throws StoredStateInvalid when the earliest time of a time window, shift or depot-hours drop
     *     is before its day
     */
    DroppedOrder droppedOrder() {
        return new DroppedOrder(order, attempt, reason.dropReason());
    }
}
