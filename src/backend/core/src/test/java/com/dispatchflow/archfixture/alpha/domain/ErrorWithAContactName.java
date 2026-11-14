package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;

/** ADR-043: a value marked as personal data is never an error parameter. */
public record ErrorWithAContactName(ContactName contact) implements DomainError {}
