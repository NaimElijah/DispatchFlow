package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;

/** Allowed: a value that wraps a String is not looked into. */
public record ErrorWithARuleCode(RuleCode rule) implements DomainError {}
