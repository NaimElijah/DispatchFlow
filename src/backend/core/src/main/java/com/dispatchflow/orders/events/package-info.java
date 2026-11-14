/**
 * The facts the Customers &amp; Orders context publishes to other modules (DL §7.2, §7.3): an order
 * cancelled, and an order moved to another date. Planning takes the stop off its plan, and
 * Messaging tells the recipient. The events carry only IDs, kernel values and numbers, never an
 * address, a name, a phone or a note.
 */
@NullMarked
package com.dispatchflow.orders.events;

import org.jspecify.annotations.NullMarked;
