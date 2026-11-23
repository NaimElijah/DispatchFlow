/**
 * The use cases of Customers &amp; Orders' {@code Order} aggregate (DL §4.3): placing, changing,
 * rescheduling, cancelling, putting back, copying and anonymizing orders, the order queries of the
 * screens, and the implementations of its published API that read orders ({@code
 * OrdersForDateService}, {@code StopOrdersService}, {@code RecipientContactsService} and {@code
 * EndSupersededPutBackService}).
 */
@NullMarked
package com.dispatchflow.orders.application.service.order;

import org.jspecify.annotations.NullMarked;
