/**
 * The {@link com.dispatchflow.planning.domain.plan.Plan} aggregate (DL §4.4, §17): one delivery
 * date's routes, stops, pins, locks and edits, with its invariants. {@link
 * com.dispatchflow.planning.domain.plan.SolveJob} and {@link
 * com.dispatchflow.planning.domain.plan.ReoptimizationProposal} share this package, with the
 * columns their values are stored in and lane capacity, because only a plan queues a job and only
 * a job creates a proposal, through package-private factories (DL §3.5, ADR-118). It also holds the
 * values only these three use, such as the publishing checks and the layout a change would leave.
 */
@NullMarked
package com.dispatchflow.planning.domain.plan;

import org.jspecify.annotations.NullMarked;
