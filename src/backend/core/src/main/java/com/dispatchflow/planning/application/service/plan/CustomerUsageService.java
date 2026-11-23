package com.dispatchflow.planning.application.service.plan;

import com.dispatchflow.orders.application.spi.RecordUsage;
import com.dispatchflow.planning.application.port.out.PlanningViews;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.PlanId;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Planning's implementation of Customers &amp; Orders' {@link RecordUsage}, which Customers &amp;
 * Orders asks before it retires a customer (BR-43, DL §2.1's dashed arrow, placement P9). It reads
 * the plans through {@link PlanningViews}.
 *
 * <p>Customers &amp; Orders names every order of the customer not yet anonymized, whatever its date,
 * so Planning answers from the orders its plans hold and keeps no customer of its own. Planning alone
 * decides which plans are not yet completed (DL §4.3).
 *
 * <p>Built in M2.
 */
@Transactional(readOnly = true)
public class CustomerUsageService implements RecordUsage {

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
    public List<PlanId> plansUsing(List<OrderId> orders) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
