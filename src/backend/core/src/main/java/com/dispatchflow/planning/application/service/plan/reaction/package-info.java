/**
 * The reactions of Planning's {@code Plan} aggregate (DL §4.4, §6.2, §8): the changes another
 * aggregate's or another module's event makes to a plan, which no dispatcher asks for. A solve job's
 * success or failure ends the plan's solve; an order cancelled, rescheduled or put back takes its stop
 * or drop off its date's plan; and Live Operations' listener asks, through Planning's {@code api}, to
 * reconcile a late outcome or to remove a superseded put-back's stop. Each takes the listener's
 * server step, is idempotent by nature, and is two classes, the use case and its {@code Change},
 * because it tells the dispatchers' open screens after its transaction (ADR-129). A plan's reactions
 * sit beneath the plan's use cases (ADR-125).
 */
@NullMarked
package com.dispatchflow.planning.application.service.plan.reaction;

import org.jspecify.annotations.NullMarked;
