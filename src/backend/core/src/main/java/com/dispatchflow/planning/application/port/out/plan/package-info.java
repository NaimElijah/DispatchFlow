/**
 * The outbound ports of Planning's {@code Plan} aggregate (DL §3.6, §13, TS §6.4): its {@link
 * com.dispatchflow.planning.application.port.out.plan.PlanRepository}, and the {@link
 * com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker} that judges an edit, a
 * hand-over, the publishing re-check and the route re-check, with its {@link
 * com.dispatchflow.planning.application.port.out.plan.EvaluationRequest}.
 */
@NullMarked
package com.dispatchflow.planning.application.port.out.plan;

import org.jspecify.annotations.NullMarked;
