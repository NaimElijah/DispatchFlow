package com.dispatchflow.planning.application.port.out.plan;

import com.dispatchflow.planning.domain.plan.RouteIndex;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RouteId;
import com.dispatchflow.shared.StopId;
import com.dispatchflow.shared.Volume;
import com.dispatchflow.shared.Weight;
import java.util.List;
import java.util.Objects;

/**
 * A driver's loading list before leaving the depot: the route's orders in reverse stop order, with
 * their weight and volume, so the last stop's goods go in first (FR-153). Package counts wait for
 * FR-136, which is not built.
 *
 * <p>To Be Implemented.
 *
 * @param route the route
 * @param index its index from 1 to 30, its label (FR-148)
 * @param items its orders, the last stop first
 */
public record LoadingListView(RouteId route, RouteIndex index, List<Item> items) {

    /** Checks the route and its index are present, and copies the items. */
    public LoadingListView {
        Objects.requireNonNull(route, "route");
        Objects.requireNonNull(index, "index");
        items = List.copyOf(items);
    }

    /**
     * One order to load.
     *
     * <p>To Be Implemented.
     *
     * @param stop the order's stop
     * @param order the order
     * @param weight its weight
     * @param volume its volume
     */
    public record Item(StopId stop, OrderId order, Weight weight, Volume volume) {

        /** Checks every part is present. */
        public Item {
            Objects.requireNonNull(stop, "stop");
            Objects.requireNonNull(order, "order");
            Objects.requireNonNull(weight, "weight");
            Objects.requireNonNull(volume, "volume");
        }
    }
}
