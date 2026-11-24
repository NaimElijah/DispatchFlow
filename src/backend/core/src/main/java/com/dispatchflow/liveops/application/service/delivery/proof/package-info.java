/**
 * The use cases of a stop's proof (DL §4.5, §17): a driver's upload of a signature or photo, and
 * the query that serves one to the staff and the driver who may see it. They change no {@code
 * Delivery} themselves; the proof's ID goes into the report that records the outcome. They sit
 * beneath the delivery's use cases, as {@code liveops.domain.proof} sits beside its delivery
 * (ADR-125).
 */
@NullMarked
package com.dispatchflow.liveops.application.service.delivery.proof;

import org.jspecify.annotations.NullMarked;
