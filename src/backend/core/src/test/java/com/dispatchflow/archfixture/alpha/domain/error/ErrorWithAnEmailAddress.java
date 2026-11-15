package com.dispatchflow.archfixture.alpha.domain.error;

import com.dispatchflow.archfixture.alpha.domain.personal.EmailAddress;
import com.dispatchflow.shared.DomainError;

/** ADR-043: an email address is personal data. */
public record ErrorWithAnEmailAddress(EmailAddress email) implements DomainError {}
