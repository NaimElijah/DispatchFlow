/**
 * Orders' persistence adapter (ADR-126, ADR-127): the JPA implementations of the repositories of
 * {@code Customer}, {@code Order} and {@code ImportBatch}, over the tables of the {@code orders}
 * schema, with the auto-applied converters of Orders' single-column values and the JSON converter
 * of an import row's errors (ADR-127 decision 7).
 *
 * <p>It will also implement the ports ADR-130 decision 4 gives it, which no class implements yet:
 * {@code OrdersViews} and {@code GeocodeRequests}.
 *
 * <p>Hibernate reads the registration below when it scans this package, so a customer's {@code
 * DailyWindow}, whose constructor checks a rule, is rebuilt through {@code
 * StoredStateInvalid.whileRestoring}: a stored window a since-tightened rule refuses is an incident,
 * not a user's refusal.
 */
@NullMarked
@EmbeddableInstantiatorRegistration(embeddableClass = DailyWindow.class, instantiator = DailyWindowInstantiator.class)
package com.dispatchflow.orders.adapter.out.persistence;

import com.dispatchflow.orders.domain.DailyWindow;
import org.hibernate.annotations.EmbeddableInstantiatorRegistration;
import org.jspecify.annotations.NullMarked;
