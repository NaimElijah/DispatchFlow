/**
 * Live Operations' use cases (DL §6.2, §13, TS §6.2), one class each, split by the aggregate they
 * change ({@code delivery}, {@code routerun} and {@code alert}, ADR-118's rule). This package holds
 * what the phone's reports share: the {@link com.dispatchflow.liveops.application.service.DeviceClock}
 * a report's time is corrected by (ADR-087), and the {@link
 * com.dispatchflow.liveops.application.service.IdempotencyKeyReused} refusal (NFR-130). It also
 * holds the {@link com.dispatchflow.liveops.application.service.RouteNotStarted} refusal, which no
 * domain type can raise (ADR-107).
 */
@NullMarked
package com.dispatchflow.liveops.application.service;

import org.jspecify.annotations.NullMarked;
