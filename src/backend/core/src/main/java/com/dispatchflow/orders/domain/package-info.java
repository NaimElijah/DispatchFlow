/**
 * The Customers &amp; Orders context's domain (DL §4.3): who receives what. It holds the {@link
 * com.dispatchflow.orders.domain.Customer}, {@link com.dispatchflow.orders.domain.Order} and {@link
 * com.dispatchflow.orders.domain.ImportBatch} aggregates, the values they are made of, the
 * lock-read {@link com.dispatchflow.orders.domain.DateCapacity}, the derived {@link
 * com.dispatchflow.orders.domain.OrderStatus}, and the context's named errors, {@link
 * com.dispatchflow.orders.domain.OrdersError}.
 *
 * <p>It is pure (DL §3.9): it imports only the JDK, JSpecify, the shared kernel, JTS for the
 * service area, and this module's own {@code events} package. Addresses, names, phones, notes and
 * locations are personal data, so their values hide themselves in {@code toString()}, and no error
 * or event carries them.
 */
@NullMarked
package com.dispatchflow.orders.domain;

import org.jspecify.annotations.NullMarked;
