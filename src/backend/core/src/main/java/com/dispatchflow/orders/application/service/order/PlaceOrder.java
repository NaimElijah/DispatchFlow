package com.dispatchflow.orders.application.service.order;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.orders.domain.OrderDetails;
import com.dispatchflow.shared.CustomerId;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.OrderId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.NotFound;
import org.springframework.transaction.annotation.Transactional;

/**
 * A dispatcher enters an order through the form (FR-09): {@code Order.place} on the date's
 * capacity, read under the date's lock (BR-101, BR-113, ADR-060), with the customer's defaults for
 * a window or service time left blank (BR-49, BR-124). The order may carry an outside reference
 * (FR-85), access details (FR-86), its own recipient (FR-87), its own note (FR-135) and the door
 * flag (FR-364); its reference must be free among the date's uncancelled orders (FR-211). An order
 * added to a date whose plan is published reaches the routes through a re-optimization (FR-11).
 * A retired or anonymized customer gets no new order (FR-193).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Domain gap: {@code Customer.orderDefaults} answers in every state, so no domain method yet
 * refuses an order for a customer that isn't active. {@code Customer.activeDefaults()} is not added
 * yet; once added, it answers an active customer's defaults and refuses a retired or anonymized one
 * as its other commands do.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class PlaceOrder {

    private final CustomerRepository customers;
    private final OrderRepository orders;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param customers where customers are kept, for the order's defaults
     * @param orders where orders are kept, with each date's capacity
     * @param ids the source of the new order's ID
     * @param clock today and the time of placing
     * @param auditLog the audit trail
     */
    public PlaceOrder(
            CustomerRepository customers, OrderRepository orders, IdGenerator ids, Clock clock, AuditLog auditLog) {
        this.customers = customers;
        this.orders = orders;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Places the order.
     *
     * <p>To Be Implemented.
     *
     * @param actor the dispatcher
     * @param customer the order's customer
     * @param date the order's date
     * @param details what the form gives
     * @return the new order's ID
     * @throws NotFound when no customer has the ID
     * @throws RuleViolation with the first of, in this order: {@code CustomerAnonymized} or {@code
     *     CustomerRetired} (FR-193); {@code DateInPast} (BR-116); {@code
     *     NonexistentLocalTime} (BR-111) or {@code WindowNotOnDate} (BR-35); {@code NoServiceTime}
     *     (BR-124); {@code DuplicateOrderReference} (FR-211); {@code DateFull} (BR-101)
     */
    public OrderId place(Actor actor, CustomerId customer, DeliveryDate date, OrderDetails details) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
