/**
 * The Planning context's domain (DL §4.4): the plan for each delivery date, the solve jobs that
 * compute it, the re-optimization proposals that change it, and the settings its solves use. It is
 * pure: it imports only the JDK, JSpecify, the shared kernel and Planning's own {@code events}
 * (DL §3.9). The decisions no document settled are in ADR-101.
 *
 * <p>The plan, its solve jobs and its proposals share {@code plan}, and {@code settings} holds the
 * settings aggregate. The families they use have a package each: {@code solution}, {@code drop},
 * {@code basis}, {@code revision} and {@code reconciliation}. This package holds the module's error, the types it
 * names, and the values that several of them use (ADR-118). It imports none of its subpackages.
 */
@NullMarked
package com.dispatchflow.planning.domain;

import org.jspecify.annotations.NullMarked;
