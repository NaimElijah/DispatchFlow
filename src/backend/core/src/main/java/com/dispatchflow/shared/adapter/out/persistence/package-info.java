/**
 * The persistence adapter every module shares (ADR-126): the auto-applied converters that store
 * the kernel's single-value types in one column each, and the instantiators that rebuild its
 * multi-column {@code @Embeddable} values. It is an adapter, not part of the kernel, so the
 * kernel's purity rules leave it out, and the kernel never names one of its classes. It will also
 * implement {@code ProcessedEvents}, which no class implements yet (ADR-130 decision 5).
 *
 * <p>Hibernate reads the registrations below when it scans this package, so {@code TimeWindow},
 * {@code GeoPoint} and {@code DaySeconds} are rebuilt through {@code
 * StoredStateInvalid.whileRestoring}: a stored value a since-tightened rule refuses is an incident,
 * not a user's refusal.
 */
@NullMarked
@EmbeddableInstantiatorRegistration(embeddableClass = TimeWindow.class, instantiator = TimeWindowInstantiator.class)
@EmbeddableInstantiatorRegistration(embeddableClass = GeoPoint.class, instantiator = GeoPointInstantiator.class)
@EmbeddableInstantiatorRegistration(embeddableClass = DaySeconds.class, instantiator = DaySecondsInstantiator.class)
package com.dispatchflow.shared.adapter.out.persistence;

import com.dispatchflow.shared.DaySeconds;
import com.dispatchflow.shared.GeoPoint;
import com.dispatchflow.shared.TimeWindow;
import org.hibernate.annotations.EmbeddableInstantiatorRegistration;
import org.jspecify.annotations.NullMarked;
