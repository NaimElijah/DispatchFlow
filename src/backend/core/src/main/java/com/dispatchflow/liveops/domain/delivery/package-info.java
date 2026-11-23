/**
 * The {@link com.dispatchflow.liveops.domain.delivery.Delivery} aggregate (DL §4.5, §9.3): the facts
 * recorded for one stop, with its invariants and the columns it is stored in, the entitlement and
 * hold check it is judged by, and {@link com.dispatchflow.liveops.domain.delivery.ArrivalProjection},
 * which reads stop facts to project when each stop is reached. The records a delivery keeps, its
 * arrival, outcomes and held reports, sit in {@code history}; the outcomes and proofs they state sit
 * in {@code liveops.domain.outcome} and {@code liveops.domain.proof} (ADR-125).
 */
@NullMarked
package com.dispatchflow.liveops.domain.delivery;

import org.jspecify.annotations.NullMarked;
