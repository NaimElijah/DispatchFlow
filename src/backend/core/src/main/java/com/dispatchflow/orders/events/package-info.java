/**
 * The facts the Customers &amp; Orders context publishes to other modules (DL §7.2, §7.3): an order
 * cancelled, an order moved to another date, and an order put back onto another date (ADR-115).
 * Planning takes the stop or the drop off its plan, and Messaging tells the recipient of a
 * cancellation or a reschedule. The events carry only IDs, kernel values and numbers, never an
 * address, a name, a phone or a note.
 */
@NullMarked
package com.dispatchflow.orders.events;

import org.jspecify.annotations.NullMarked;
