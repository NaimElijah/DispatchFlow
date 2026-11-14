package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;

/** Allowed: a cycle of values that hold no personal data. */
public record ErrorWithALinkedCode(LinkedCode chain) implements DomainError {}
