package com.dispatchflow.orders.application.service;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.orders.application.port.out.CustomerRepository;
import com.dispatchflow.orders.application.port.out.OrderRepository;
import com.dispatchflow.shared.DeliveryDate;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * A dispatcher copies a previous date's orders to a new date, to change them before solving
 * (FR-62, a Should row). Each copy is a new order placed on the new date's capacity, read under its
 * lock, with its window moved to the new date, so the limit counts it (BR-113) and its reference
 * is checked there (FR-211, ADR-102). The new orders are created together in one transaction (DL
 * §6.1 point 1). An order whose customer was retired or anonymized since refuses the whole copy,
 * so nothing is copied (FR-193).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Domain gap: no domain method copies an order, and {@code Order} gives its details only through
 * its snapshot. The milestone that builds this adds the method, for example a factory that places
 * an order's details on another date's capacity. Each copy takes its customer's defaults through
 * {@code Customer.activeDefaults()}, which M2 adds for {@link PlaceOrder}, so a customer that isn't
 * active refuses.
 *
 * <p>Built in an unscheduled milestone.
 */
public final class CopyOrdersFromDate {

    private final OrderRepository orders;
    private final CustomerRepository customers;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param orders where orders are kept, for the date copied from and the new date's capacity
     * @param customers where customers are kept, for each copy's defaults
     * @param ids the source of the new orders' IDs
     * @param clock today and the time of placing
     * @param auditLog the audit trail
     */
    public CopyOrdersFromDate(
            OrderRepository orders, CustomerRepository customers, IdGenerator ids, Clock clock, AuditLog auditLog) {
        this.orders = orders;
        this.customers = customers;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Copies every uncancelled order of one date to another.
     *
     * <p>Built in an unscheduled milestone.
     *
     * @param actor the dispatcher
     * @param from the date copied from
     * @param to the new date
     * @return how many orders were copied, 0 or more
     * @throws RuleViolation with the first refusal for any copy, so nothing is copied: {@code
     *     CustomerAnonymized} or {@code CustomerRetired} for its customer (FR-193), or the first
     *     refusal of {@code Order.place}, such as {@code DateInPast} (BR-116), {@code
     *     DuplicateOrderReference} (FR-211) or {@code DateFull} (BR-101)
     */
    public int copy(Actor actor, DeliveryDate from, DeliveryDate to) {
        throw new UnsupportedOperationException("Built in an unscheduled milestone");
    }
}
