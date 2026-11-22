/**
 * The error-shape fixtures: the clean controls, an error interface that is not sealed, an error that
 * declares its own code, and the codes some of them carry. The errors whose parameters could carry
 * text sit in {@code text}, and those whose parameters could carry personal data in {@code
 * personalvalue} (ADR-125).
 */
@NullMarked
package com.dispatchflow.archfixture.alpha.domain.error;

import org.jspecify.annotations.NullMarked;
