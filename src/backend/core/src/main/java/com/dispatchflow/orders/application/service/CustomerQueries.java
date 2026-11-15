package com.dispatchflow.orders.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.orders.application.port.out.CustomerList;
import com.dispatchflow.orders.application.port.out.CustomerView;
import com.dispatchflow.orders.application.port.out.FlaggedAddresses;
import com.dispatchflow.orders.application.port.out.OrdersViews;
import com.dispatchflow.orders.domain.Address;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.PhoneNumber;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import java.util.Optional;

/**
 * The customer screens' reads (FR-56, FR-63, FR-134, FR-149, FR-212, FR-216, FR-259): finding a
 * customer, one customer's page, the warning about a likely duplicate, and the flagged addresses
 * checked before a solve. Every read is the dispatcher's, authorized by role from its operation
 * (ADR-068); no driver scope applies (BR-02), so no check depends on the data.
 *
 * <p>Built in M2, M7 and an unscheduled milestone, method by method.
 */
public final class CustomerQueries {

    private final OrdersViews views;
    private final Clock clock;

    /**
     * Creates the queries.
     *
     * @param views Customers &amp; Orders' read port
     * @param clock today, from which a customer's orders are listed
     */
    public CustomerQueries(OrdersViews views, Clock clock) {
        this.views = views;
        this.clock = clock;
    }

    /**
     * Finds customers by name, phone or address (FR-134), so the dispatcher also finds an order by
     * its customer (FR-259).
     *
     * <p>Built in M2.
     *
     * @param actor the dispatcher
     * @param text what the dispatcher typed
     * @return the customers found, retired ones included, ordered by name
     */
    public CustomerList search(Actor actor, String text) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * One customer's page, with its orders from today on (FR-56, FR-259).
     *
     * <p>Built in M2.
     *
     * @param actor the dispatcher
     * @param customer the customer
     * @return the customer
     * @throws NotFound when no customer has the ID
     */
    public CustomerView show(Actor actor, CustomerId customer) {
        throw new UnsupportedOperationException("Built in M2");
    }

    /**
     * The active customers a customer being saved likely duplicates, with the same phone or a
     * matching address, which the dispatcher is warned about before saving (FR-212). The warning
     * never refuses the save.
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param actor the dispatcher
     * @param phone the phone being saved
     * @param address the address being saved, matched by its normalized text (FR-398)
     * @param except the customer being changed, which is not its own duplicate; empty for a new one
     * @return the likely duplicates, possibly none
     */
    public CustomerList likelyDuplicates(Actor actor, PhoneNumber phone, Address address, Optional<CustomerId> except) {
        throw new UnsupportedOperationException("Built in an unscheduled milestone");
    }

    /**
     * Every active customer whose address is flagged: no, a partial or an ambiguous match, outside
     * the service area, or far from a road (FR-63). The map shows them apart from routed stops
     * (FR-149), and no flag stops a solve (FR-216).
     *
     * <p>Built in M7.
     *
     * @param actor the dispatcher
     * @return the flagged customers
     */
    public FlaggedAddresses flagged(Actor actor) {
        throw new UnsupportedOperationException("Built in M7");
    }
}
