/**
 * The edit use cases of Planning's {@code Plan} aggregate (DL §4.4, §6.3): moving a stop, pinning
 * and unpinning an order, placing a dropped order, taking a stop off its route, and undoing and
 * redoing an edit.
 *
 * <p>An edit asks the optimizer to judge the plan as the edit would leave it, through the plan's
 * {@code after…} query, before its transaction opens; the command then leaves exactly that layout (DL
 * §6.3, ADR-112).
 */
@NullMarked
package com.dispatchflow.planning.application.service.plan.edit;

import org.jspecify.annotations.NullMarked;
