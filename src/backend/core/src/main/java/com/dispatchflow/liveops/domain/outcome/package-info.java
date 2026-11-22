/**
 * Live Operations' outcomes (DL §4.5, §17): a stop's outcome, delivered or failed, where the first
 * one wins, a failure's reason and its kind, and the outcome a dispatcher records. A delivered
 * outcome carries its proof, so this package uses {@code proof}, one way (ADR-125).
 */
@NullMarked
package com.dispatchflow.liveops.domain.outcome;

import org.jspecify.annotations.NullMarked;
