/**
 * What the optimizer hands back (DL §13, §17): the routes, stops and drops of a solve or a
 * re-optimization, the {@link com.dispatchflow.planning.domain.solution.ValidationReport} and
 * {@link com.dispatchflow.planning.domain.solution.Evaluation} that judge them, their violations,
 * times and objective terms, the vehicles a solve excluded or withdrew, and why a solve failed.
 */
@NullMarked
package com.dispatchflow.planning.domain.solution;

import org.jspecify.annotations.NullMarked;
