/**
 * Fleet's persistence adapter (ADR-126, ADR-127): the JPA implementations of the repositories of
 * {@code Driver}, {@code FleetRegister} and {@code DayRoster}, over the tables of the {@code fleet}
 * schema, with the auto-applied converters of Fleet's single-column values.
 *
 * <p>Hibernate reads the registrations below when it scans this package, so {@code ShiftHours} and
 * {@code DepotHours}, whose constructors check a rule, are rebuilt through {@code
 * StoredStateInvalid.whileRestoring}: stored hours a since-tightened rule refuses are an incident,
 * not a user's refusal. Fleet's other multi-column values ({@code DriverContact}, {@code
 * VehicleDetails}, {@code Capacity}) only check their parts are present, so Hibernate builds them
 * through their canonical constructors.
 */
@NullMarked
@EmbeddableInstantiatorRegistration(embeddableClass = ShiftHours.class, instantiator = ShiftHoursInstantiator.class)
@EmbeddableInstantiatorRegistration(embeddableClass = DepotHours.class, instantiator = DepotHoursInstantiator.class)
package com.dispatchflow.fleet.adapter.out.persistence;

import com.dispatchflow.fleet.domain.ShiftHours;
import com.dispatchflow.fleet.domain.register.DepotHours;
import org.hibernate.annotations.EmbeddableInstantiatorRegistration;
import org.jspecify.annotations.NullMarked;
