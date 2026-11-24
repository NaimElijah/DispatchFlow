/**
 * The clock adapter every module shares (ADR-130): implements {@code Clock}, with the system clock
 * in production and, in the end-to-end profile, a clock a test can set.
 */
@NullMarked
package com.dispatchflow.shared.adapter.out.clock;

import org.jspecify.annotations.NullMarked;
