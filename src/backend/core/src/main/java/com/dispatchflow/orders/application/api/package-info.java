/**
 * Customers &amp; Orders' published API (DL §2.2, §2.3 rule 4): {@link
 * com.dispatchflow.orders.application.api.OrdersForDate}, a date's orders as Planning and Live
 * Operations read them; {@link com.dispatchflow.orders.application.api.StopOrders}, what a driver
 * sees and is judged by at a stop, which Planning's printable route also prints; {@link
 * com.dispatchflow.orders.application.api.RecipientContacts}, the phones Messaging chooses from;
 * {@link com.dispatchflow.orders.application.api.ServiceAreaCheck}, whether a vehicle's position
 * lies inside the service area, which Planning asks (ADR-050); and {@link
 * com.dispatchflow.orders.application.api.EndSupersededPutBack}, which Live Operations calls to
 * return an order to its earlier attempt after a superseded put-back (FR-404, ADR-121).
 *
 * <p>Its types use only kernel types, JDK types, their own records, and Audit's {@code
 * CallContext}, never a domain type (DL §2.3 rule 1). A record that holds a phone, a point or free
 * text is personal data and prints redacted (NFR-19, ADR-106).
 */
@NullMarked
package com.dispatchflow.orders.application.api;

import org.jspecify.annotations.NullMarked;
