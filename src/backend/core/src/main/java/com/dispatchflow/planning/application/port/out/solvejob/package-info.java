/**
 * The outbound ports of Planning's {@code SolveJob} aggregate (DL §3.6, §13, TS §6.4, ADR-051): its
 * {@link com.dispatchflow.planning.application.port.out.solvejob.SolveJobRepository}, and the {@link
 * com.dispatchflow.planning.application.port.out.solvejob.RouteOptimizer} a job's worker calls, with
 * its requests and its answers.
 *
 * <p>An answer carries a {@code ValidatedSolution}, a {@code ReoptimizationResult} or a {@code
 * SolveFailure}. Only the optimizer adapter builds the {@code ValidationReport} behind a validated
 * solution (DL §13), so no other code path can hand a job an unvalidated one (BR-15).
 */
@NullMarked
package com.dispatchflow.planning.application.port.out.solvejob;

import org.jspecify.annotations.NullMarked;
