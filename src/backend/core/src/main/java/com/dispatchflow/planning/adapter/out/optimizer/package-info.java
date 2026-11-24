/**
 * Planning's optimizer adapter (ADR-130): implements {@code RouteOptimizer} and {@code
 * FeasibilityChecker} over the shared optimizer client, as an anti-corruption layer. Only this
 * package builds a {@code ValidationReport} or an {@code Evaluation} (DL §13).
 */
@NullMarked
package com.dispatchflow.planning.adapter.out.optimizer;

import org.jspecify.annotations.NullMarked;
