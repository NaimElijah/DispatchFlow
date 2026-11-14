package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;

/** ADR-043: an address is personal data. */
public record ErrorWithANormalizedAddress(NormalizedAddress address) implements DomainError {}
