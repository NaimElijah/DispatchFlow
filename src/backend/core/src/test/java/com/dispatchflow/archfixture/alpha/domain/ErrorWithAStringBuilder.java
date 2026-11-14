package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;

/** ADR-043: any CharSequence is free text. */
public record ErrorWithAStringBuilder(StringBuilder reason) implements DomainError {}
