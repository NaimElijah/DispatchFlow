package com.dispatchflow.orders.domain;

import java.util.List;

/**
 * What a confirmed import creates, which the use case saves with the batch in one transaction (DL
 * §4.3, §6.1): the new customers, and every row's order.
 *
 * @param customers the customers the import created, in the order of their first row
 * @param orders one order per row, in row order
 */
public record ImportResult(List<Customer> customers, List<Order> orders) {

    /** Copies the lists. */
    public ImportResult {
        customers = List.copyOf(customers);
        orders = List.copyOf(orders);
    }
}
