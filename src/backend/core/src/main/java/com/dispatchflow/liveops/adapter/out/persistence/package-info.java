/**
 * Live Operations' persistence adapter (ADR-126, ADR-127): the JPA implementations of the
 * repositories of {@code Delivery}, {@code RouteRun} and {@code Alert} and of the position log, over
 * the tables of the {@code liveops} schema. It holds the auto-applied converters of Live
 * Operations' single-column values, and the {@code @Immutable} entity of the position record, which
 * the record type itself can't be (ADR-127 decision 6).
 *
 * <p>Hibernate reads the registration below when it scans this package, so a stored {@code
 * CorrectedTime} is rebuilt through {@code StoredStateInvalid.whileRestoring}: a device instant its
 * range now refuses is an incident, not a refusal.
 */
@NullMarked
@EmbeddableInstantiatorRegistration(
        embeddableClass = CorrectedTime.class,
        instantiator = CorrectedTimeInstantiator.class)
package com.dispatchflow.liveops.adapter.out.persistence;

import com.dispatchflow.liveops.domain.CorrectedTime;
import org.hibernate.annotations.EmbeddableInstantiatorRegistration;
import org.jspecify.annotations.NullMarked;
