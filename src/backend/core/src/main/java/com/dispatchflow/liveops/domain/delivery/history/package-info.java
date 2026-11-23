/**
 * A stop's history, as its {@link com.dispatchflow.liveops.domain.delivery.Delivery} keeps it (DL
 * §4.5, FR-333, BR-132): the driver's arrival, each outcome on record with how it came to be
 * recorded, and the reports held for a dispatcher with where each stands. These are the public
 * values callers and views read; the delivery stores them in columns of its own and keeps their
 * invariants. They depend on nothing in the {@code delivery} package, so the two have no cycle
 * (ADR-125).
 */
@NullMarked
package com.dispatchflow.liveops.domain.delivery.history;

import org.jspecify.annotations.NullMarked;
