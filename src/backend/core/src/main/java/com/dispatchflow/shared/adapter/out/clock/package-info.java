/**
 * The clock adapter every module shares (ADR-130): implements {@code Clock} with the system clock.
 * A clock a test can set comes with the end-to-end profile (NFR-175).
 */
@NullMarked
package com.dispatchflow.shared.adapter.out.clock;

import org.jspecify.annotations.NullMarked;
