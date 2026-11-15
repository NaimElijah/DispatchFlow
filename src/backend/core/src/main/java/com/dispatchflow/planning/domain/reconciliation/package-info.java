/**
 * How a plan reconciles a late fact (DL §8, §17): the {@link
 * com.dispatchflow.planning.domain.reconciliation.LateFact} Live Operations asks it to reconcile,
 * the {@link com.dispatchflow.planning.domain.reconciliation.Reconciliation} that says what
 * changed, with its kinds, and the {@link
 * com.dispatchflow.planning.domain.reconciliation.LateOutcome} that says how the stop came onto the
 * route that served it.
 */
@NullMarked
package com.dispatchflow.planning.domain.reconciliation;

import org.jspecify.annotations.NullMarked;
