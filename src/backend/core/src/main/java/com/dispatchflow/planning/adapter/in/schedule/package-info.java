/**
 * Planning's timed triggers (ADR-130): {@code CloseDeliveryDate} at midnight, {@code RecheckRoutes}
 * every minute, the queue watchdog {@code ExpireOverdueSolveJobs} every 15 seconds (ADR-051), and
 * the nightly retention step {@code EraseExpiredLocations}.
 */
@NullMarked
package com.dispatchflow.planning.adapter.in.schedule;

import org.jspecify.annotations.NullMarked;
