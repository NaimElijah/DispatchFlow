/**
 * Customers &amp; Orders' use cases (DL §4.3, §6.2, §13; TS §6.2): one class per command and per
 * job, the implementations of its published API ({@code OrdersForDateService}, {@code
 * StopOrdersService}, {@code RecipientContactsService}, {@code ServiceAreaCheckService} and {@code
 * EndSupersededPutBackService}), and the query classes of its screens ({@code CustomerQueries},
 * {@code OrderQueries} and {@code ImportQueries}).
 *
 * <p>A command makes its outside calls first, then changes one existing aggregate in one
 * transaction, and writes its audit record in it (DL §6.1, §6.3; NFR-23). Each body throws until
 * the milestone its Javadoc names builds it.
 */
@NullMarked
package com.dispatchflow.orders.application.service;

import org.jspecify.annotations.NullMarked;
