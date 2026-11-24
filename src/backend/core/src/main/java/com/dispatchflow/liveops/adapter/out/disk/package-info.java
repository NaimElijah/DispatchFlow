/**
 * Live Operations' proof store (ADR-027, ADR-130): implements {@code ProofStore} on the VM's disk,
 * with the store's sweep. The files' bindings and tombstones are rows, so it reaches them through a
 * port that the module's {@code persistence} adapter implements, never through a class of that
 * package.
 */
@NullMarked
package com.dispatchflow.liveops.adapter.out.disk;

import org.jspecify.annotations.NullMarked;
