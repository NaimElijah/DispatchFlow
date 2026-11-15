package com.dispatchflow.archfixture.alpha.domain.error;

import com.dispatchflow.archfixture.alpha.domain.personal.Recipients;
import com.dispatchflow.shared.DomainError;

/** ADR-106: personal values in an array inside a wrapper are still personal data. */
public record ErrorWithRecipients(Recipients recipients) implements DomainError {}
