package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.PhoneNumber;

/** ADR-043: an error parameter carries no personal data or free text. */
public record ErrorWithAPhone(PhoneNumber phone) implements DomainError {}
