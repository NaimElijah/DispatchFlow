/**
 * Live Operations' outcomes (DL §4.5, §17): a stop's outcome, delivered or failed, where the first
 * one wins, a failure's reason and its kind, and the outcome a dispatcher records. With them, the
 * outcomes a delivery keeps on record (FR-333, BR-132): each original or correction with how it
 * came to be recorded and why a dispatcher corrected it, and each report held for a dispatcher with
 * where it stands. The delivery stores those records in columns of its own and keeps their
 * invariants, so no class here depends on {@code delivery}. A delivered outcome carries its proof,
 * so this package uses {@code proof}, one way (ADR-125).
 */
@NullMarked
package com.dispatchflow.liveops.domain.outcome;

import org.jspecify.annotations.NullMarked;
