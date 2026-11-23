/**
 * Live Operations' proofs of delivery (DL §4.5, §17): a signature or a photo, the uploaded file it
 * names, its kind and an optional note (FR-30, FR-367). A driver's delivered outcome needs one.
 * These values use nothing in {@code delivery} or {@code outcome} (ADR-125).
 */
@NullMarked
package com.dispatchflow.liveops.domain.proof;

import org.jspecify.annotations.NullMarked;
