/**
 * The outbound ports of Planning's {@code Plan} aggregate (DL §3.6, §13, TS §6.4): its {@link
 * com.dispatchflow.planning.application.port.out.plan.PlanRepository}, and the {@link
 * com.dispatchflow.planning.application.port.out.plan.FeasibilityChecker} that judges an edit, a
 * hand-over, the publishing re-check and the route re-check, with its {@link
 * com.dispatchflow.planning.application.port.out.plan.EvaluationRequest}. Also the road network's
 * {@link com.dispatchflow.planning.application.port.out.plan.RouteShapes}, with the line and the view
 * the map draws, and the views of one plan's routes that only its queries read: the loading list, the
 * printable route and the pre-flight check.
 */
@NullMarked
package com.dispatchflow.planning.application.port.out.plan;

import org.jspecify.annotations.NullMarked;
