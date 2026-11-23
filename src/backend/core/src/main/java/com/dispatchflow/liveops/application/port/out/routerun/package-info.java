/**
 * The views of Live Operations' screens that only the {@code RouteRun} queries read (DL §4.5,
 * FR-34, FR-251): the stops at risk, the unconfirmed stops, the live map, a route's progress, and
 * a driver's day and the routes ahead. They use the root's {@code RunState}, one way (ADR-125). A
 * view that holds a position or a recipient's details is personal data and prints redacted
 * (NFR-19, ADR-106).
 */
@NullMarked
package com.dispatchflow.liveops.application.port.out.routerun;

import org.jspecify.annotations.NullMarked;
