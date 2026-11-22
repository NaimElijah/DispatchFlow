/**
 * What the optimizer hands back (DL §13, §17): the routes and stops of a solve or a
 * re-optimization, the {@link com.dispatchflow.planning.domain.solution.ValidationReport} and
 * {@link com.dispatchflow.planning.domain.solution.Evaluation} that judge them, their violations,
 * times and objective terms, the vehicles a re-optimization withdrew, and why a solve failed. Its
 * drops and the vehicles a solve excluded sit in {@code planning.domain.drop}, which it uses.
 */
@NullMarked
package com.dispatchflow.planning.domain.solution;

import org.jspecify.annotations.NullMarked;
