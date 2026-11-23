/**
 * The {@link com.dispatchflow.liveops.domain.delivery.Delivery} aggregate (DL §4.5, §9.3): the facts
 * recorded for one stop, with its snapshot and invariants, the entitlement and hold check it is
 * judged by, and {@link com.dispatchflow.liveops.domain.delivery.ArrivalProjection}, which reads stop
 * facts to project when each stop is reached. The outcomes and proofs it records sit in {@code
 * liveops.domain.outcome} and {@code liveops.domain.proof} (ADR-125).
 */
@NullMarked
package com.dispatchflow.liveops.domain.delivery;

import org.jspecify.annotations.NullMarked;
