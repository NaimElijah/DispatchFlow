/**
 * The Planning context's domain (DL §4.4): the plan for each delivery date, the solve jobs that
 * compute it, the re-optimization proposals that change it, and the settings its solves use. It is
 * pure: it imports only the JDK, JSpecify, the shared kernel and Planning's own {@code events}
 * (DL §3.9). The decisions no document settled are in ADR-101.
 */
@NullMarked
package com.dispatchflow.planning.domain;

import org.jspecify.annotations.NullMarked;
