/**
 * Customers &amp; Orders' outbound ports (DL §3.6, §13; TS §6.4): the repositories of its three
 * aggregates ({@link com.dispatchflow.orders.application.port.out.CustomerRepository}, {@link
 * com.dispatchflow.orders.application.port.out.OrderRepository} and {@link
 * com.dispatchflow.orders.application.port.out.ImportBatchRepository}), the {@link
 * com.dispatchflow.orders.application.port.out.Geocoder} and its outbox {@link
 * com.dispatchflow.orders.application.port.out.GeocodeRequests}, the road network's {@link
 * com.dispatchflow.orders.application.port.out.RoadSnap}, the {@link
 * com.dispatchflow.orders.application.port.out.ServiceAreas}, and the read port {@link
 * com.dispatchflow.orders.application.port.out.OrdersViews} with the view records the queries
 * answer with.
 *
 * <p>Adapters implement every port here. A record that holds a name, a phone, an address, a point
 * or a note is personal data and prints redacted (NFR-19, ADR-106).
 */
@NullMarked
package com.dispatchflow.orders.application.port.out;

import org.jspecify.annotations.NullMarked;
