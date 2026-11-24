/**
 * Planning's optimizer client (ADR-130): implements {@code RouteOptimizer} and {@code
 * FeasibilityChecker} over {@code optimization-api} v1, as an anti-corruption layer. Only this
 * package builds a {@code ValidationReport} or an {@code Evaluation} (DL §13).
 */
@NullMarked
package com.dispatchflow.planning.adapter.out.optimizer;

import org.jspecify.annotations.NullMarked;
