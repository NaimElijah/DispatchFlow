/**
 * The use cases that change a stop's {@code Delivery} (DL §4.5, §6.2, §8): the driver's report and
 * its proof, a dispatcher's record on a driver's behalf, a correction, a decision on a held report,
 * the listeners that reconcile a late outcome and take a superseded put-back off, and the retention
 * job. It also holds the implementations of Live Operations' {@code ArrivalProjections}, of
 * Planning's {@code ExecutionFacts} and of Customers &amp; Orders' {@code StopProgress}, which read
 * deliveries, and the proof query.
 */
@NullMarked
package com.dispatchflow.liveops.application.service.delivery;

import org.jspecify.annotations.NullMarked;
