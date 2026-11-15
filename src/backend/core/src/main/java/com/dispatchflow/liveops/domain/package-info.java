/**
 * The Live Operations context's domain (DL §4.5): what happened on the street. Each aggregate has
 * its own package: {@link com.dispatchflow.liveops.domain.delivery.Delivery} holds one stop's facts,
 * {@link com.dispatchflow.liveops.domain.routerun.RouteRun} a route being driven, and {@link
 * com.dispatchflow.liveops.domain.alert.Alert} something a dispatcher must look at, each with its
 * values. This package holds the named errors ({@link
 * com.dispatchflow.liveops.domain.LiveOpsError}), the types they name, and the values that more than
 * one aggregate uses (ADR-118). ADR-107 records the rules DL left open.
 *
 * <p>It is pure (DL §3.9): it imports only the JDK, JSpecify, the shared kernel and its own {@code
 * events} package, and takes {@code now} and every outside fact as parameters.
 */
@NullMarked
package com.dispatchflow.liveops.domain;

import org.jspecify.annotations.NullMarked;
