package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;

/** ADR-043: an email address is personal data. */
public record ErrorWithAnEmailAddress(EmailAddress email) implements DomainError {}
