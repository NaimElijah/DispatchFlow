package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.orders.application.spi.RecordUsage;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.PlanId;
import java.util.List;

/**
 * Planning's implementation of Customers &amp; Orders' {@link RecordUsage}, which Customers &amp;
 * Orders asks before it retires a customer (BR-43, DL §2.1's dashed arrow, placement P9). It reads
 * the plans through {@link PlanningViews}.
 *
 * <p>Planning keeps no customer of its own: a plan holds orders, and {@code OrderFact} doesn't name
 * the order's customer yet. So this answer needs each stop's customer kept from the solve's input,
 * which waits for {@code OrderFact} to carry it.
 *
 * <p>Built in M2.
 */
public final class CustomerUsageService implements RecordUsage {

    private final PlanningViews views;

    /**
     * Creates the service.
     *
     * @param views Planning's read side
     */
    public CustomerUsageService(PlanningViews views) {
        this.views = views;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M2.
     */
    @Override
    public List<PlanId> plansUsing(CustomerId customer) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
