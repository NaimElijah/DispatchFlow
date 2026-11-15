package com.dispatchflow.archfixture.alpha.domain.error;

import com.dispatchflow.archfixture.alpha.domain.personal.Recipient;
import com.dispatchflow.shared.DomainError;

/** ADR-106: a sealed type is personal data when any of its variants holds a personal value. */
public record ErrorWithARecipient(Recipient recipient) implements DomainError {}
