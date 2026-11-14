/**
 * The Fleet context's domain (DL §4.2): the fleet register of depots and vehicles, drivers, and the
 * roster of each delivery date, with their values and named errors ({@link
 * com.dispatchflow.fleet.domain.FleetError}). ADR-103 records the rules DL left open: plates,
 * depot hours, weekly shifts and driver retirement.
 *
 * <p>It is pure (DL §3.9): it imports only the JDK, JSpecify, the shared kernel and its own {@code
 * events} package, and takes {@code now}, {@code today} and every outside fact as parameters.
 */
@NullMarked
package com.dispatchflow.fleet.domain;

import org.jspecify.annotations.NullMarked;
