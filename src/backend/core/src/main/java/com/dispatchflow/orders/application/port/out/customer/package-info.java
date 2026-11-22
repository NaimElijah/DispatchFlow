/**
 * Customers &amp; Orders' outbound ports that only its customer use cases use: the customer list
 * and flagged-address views, and the road network's {@code RoadSnap} with the refusal it raises.
 * Adapters implement them. A record that holds a name, a phone, an address or a point is personal
 * data and prints redacted (NFR-19, ADR-106).
 */
@NullMarked
package com.dispatchflow.orders.application.port.out.customer;

import org.jspecify.annotations.NullMarked;
