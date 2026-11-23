/**
 * The use cases that change a stop's {@code Delivery} (DL §4.5, §6.2, §8): the driver's report and
 * its proof, the listeners that reconcile a late outcome and take a superseded put-back off, and the
 * retention job. A dispatcher's use cases on a delivery, a record on a driver's behalf, a correction
 * and a decision on a held report, sit in {@code dispatcher} below (ADR-125). It also holds the
 * implementations of Live Operations' {@code ArrivalProjections}, of Planning's {@code
 * ExecutionFacts} and of Customers &amp; Orders' {@code StopProgress}, which read deliveries, and the
 * proof query.
 */
@NullMarked
package com.dispatchflow.liveops.application.service.delivery;

import org.jspecify.annotations.NullMarked;
