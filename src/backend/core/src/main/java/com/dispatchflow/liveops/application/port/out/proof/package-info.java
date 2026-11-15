/**
 * Live Operations' proof store (ADR-027, NFR-38, TS §6.4): the {@link
 * com.dispatchflow.liveops.application.port.out.proof.ProofStore} port where a {@code Delivery}'s
 * proof files are kept, the {@link com.dispatchflow.liveops.application.port.out.proof.ProofUpload}
 * it takes and gives back, and the refusals it raises on an upload (FR-367, NFR-37, NFR-205,
 * ADR-087). It is a subpackage of {@code port.out} because that package would hold more than 30
 * files with it (ADR-118's rule, carried to the application). The adapter implements it.
 *
 * <p>A proof's file identifies a person, so {@code ProofUpload} prints redacted (NFR-19, ADR-106).
 */
@NullMarked
package com.dispatchflow.liveops.application.port.out.proof;

import org.jspecify.annotations.NullMarked;
