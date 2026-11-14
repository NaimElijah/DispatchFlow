package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;

/** ADR-043: an Object could be anything. */
public record ErrorWithAnObject(Object detail) implements DomainError {}
