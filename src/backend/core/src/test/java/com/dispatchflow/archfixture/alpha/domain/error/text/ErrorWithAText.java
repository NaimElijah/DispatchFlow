package com.dispatchflow.archfixture.alpha.domain.error.text;

import com.dispatchflow.shared.DomainError;

/** ADR-043: an error parameter carries no personal data or free text. */
public record ErrorWithAText(String reason) implements DomainError {}
