/**
 * The Live Operations context's domain (DL §4.5): what happened on the street. {@link
 * com.dispatchflow.liveops.domain.Delivery} holds one stop's facts, {@link
 * com.dispatchflow.liveops.domain.RouteRun} a route being driven, and {@link
 * com.dispatchflow.liveops.domain.Alert} something a dispatcher must look at, with their values and
 * named errors ({@link com.dispatchflow.liveops.domain.LiveOpsError}). ADR-107 records the rules DL
 * left open.
 *
 * <p>It is pure (DL §3.9): it imports only the JDK, JSpecify, the shared kernel and its own {@code
 * events} package, and takes {@code now} and every outside fact as parameters.
 */
@NullMarked
package com.dispatchflow.liveops.domain;

import org.jspecify.annotations.NullMarked;
