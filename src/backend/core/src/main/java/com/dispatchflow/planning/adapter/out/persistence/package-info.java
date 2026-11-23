/**
 * Planning's persistence adapter (ADR-126, ADR-127): the JPA implementations of the repositories of
 * {@code SolveJob}, {@code ReoptimizationProposal} and {@code PlanningSettings}, over the tables of
 * the {@code planning} schema, with the auto-applied converters of Planning's single-column values.
 * {@code Plan}'s repository and the entities its revisions are appended as live in the {@code plan}
 * subpackage, which these converters serve too (ADR-125).
 *
 * <p>Hibernate reads the registrations below when it scans this package, so {@code TravelBucket} and
 * {@code TravelBuckets}, whose constructors check a rule, are rebuilt through {@code
 * StoredStateInvalid.whileRestoring}: stored buckets a since-tightened rule refuses are an incident,
 * not a user's refusal. Planning's other multi-column values ({@code ProposalBasis}, {@code
 * ObjectiveTerms}, {@code ExcludedVehicle}, {@code VehicleWithdrawal}, {@code LateStop}) only check
 * their parts are present, so Hibernate builds them through their canonical constructors. The values
 * with sealed or optional parts are stored through the domain's own private embeddables, which
 * rebuild them (ADR-127 decision 3).
 */
@NullMarked
@EmbeddableInstantiatorRegistration(embeddableClass = TravelBucket.class, instantiator = TravelBucketInstantiator.class)
@EmbeddableInstantiatorRegistration(
        embeddableClass = TravelBuckets.class,
        instantiator = TravelBucketsInstantiator.class)
package com.dispatchflow.planning.adapter.out.persistence;

import com.dispatchflow.planning.domain.settings.TravelBucket;
import com.dispatchflow.planning.domain.settings.TravelBuckets;
import org.hibernate.annotations.EmbeddableInstantiatorRegistration;
import org.jspecify.annotations.NullMarked;
