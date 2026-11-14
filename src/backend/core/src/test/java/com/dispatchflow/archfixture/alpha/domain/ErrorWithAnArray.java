package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;

/** ADR-043: no arrays in params. */
public record ErrorWithAnArray(int[] rows) implements DomainError {}
