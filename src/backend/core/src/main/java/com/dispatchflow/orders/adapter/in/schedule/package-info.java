/**
 * Customers & Orders' timed triggers (ADR-130): the nightly retention steps {@code
 * ForgetUnusedGeocodes} and {@code AnonymizeCustomers}, {@code AnonymizeOrders} at 03:00, and the
 * hourly {@code ExpireImportPreviews}.
 */
@NullMarked
package com.dispatchflow.orders.adapter.in.schedule;

import org.jspecify.annotations.NullMarked;
