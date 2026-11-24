/**
 * Customers &amp; Orders' use cases (DL §4.3, §6.2, §13; TS §6.2): one class per command and per
 * job, the implementations of its published API, and the query classes of its screens. Each
 * aggregate's sit in its own package, {@code customer}, {@code order} and {@code importbatch}
 * (ADR-125). This package holds the two that use no aggregate: the job that forgets unused geocodes,
 * and {@code ServiceAreaCheckService}, which implements the published service-area check.
 *
 * <p>A command makes its outside calls first, then changes one existing aggregate in one
 * transaction, and writes its audit record in it (DL §6.1, §6.3; NFR-23). Each body throws until
 * it is built.
 */
@NullMarked
package com.dispatchflow.orders.application.service;

import org.jspecify.annotations.NullMarked;
