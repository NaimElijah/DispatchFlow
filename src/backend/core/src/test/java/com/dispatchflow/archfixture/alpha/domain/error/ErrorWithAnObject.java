package com.dispatchflow.archfixture.alpha.domain.error;

import com.dispatchflow.shared.DomainError;

/** ADR-043: an Object could be anything. */
public record ErrorWithAnObject(Object detail) implements DomainError {}
