/**
 * Planning's outbound ports that several of its aggregates use (DL §13, TS §6.4): the facts a solve,
 * a re-optimization and an evaluation share ({@link
 * com.dispatchflow.planning.application.port.out.ProblemFacts}), the road network's {@link
 * com.dispatchflow.planning.application.port.out.TravelTimes}, the {@link
 * com.dispatchflow.planning.application.port.out.StoredMatrices} each plan keeps (ADR-052), the
 * {@link com.dispatchflow.planning.application.port.out.Seeds}, the {@link
 * com.dispatchflow.planning.application.port.out.LiveUpdates} the screens follow, and the read port
 * {@link com.dispatchflow.planning.application.port.out.PlanningViews} with its view records.
 *
 * <p>An outside system that fails throws a named, unchecked port error, one per system: {@link
 * com.dispatchflow.planning.application.port.out.RoadNetworkUnavailable} for the road network, and
 * {@link com.dispatchflow.planning.application.port.out.OptimizerUnavailable} or {@link
 * com.dispatchflow.planning.application.port.out.OptimizerRefusedRequest} for the optimizer. They sit
 * here because the ports of several aggregates throw them.
 *
 * <p>The ports of one aggregate sit in its subpackage: {@code plan}, {@code solvejob}, {@code
 * proposal} and {@code settings} (ADR-118's rule, ADR-125). This root imports none of them. The read port
 * sits here, with every view it returns, because the query classes of every aggregate read it.
 *
 * <p>A record that holds a location or another person's data is personal data and prints redacted
 * (NFR-19, ADR-106).
 */
@NullMarked
package com.dispatchflow.planning.application.port.out;

import org.jspecify.annotations.NullMarked;
