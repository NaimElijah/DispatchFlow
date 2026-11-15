package com.dispatchflow.archfixture.alpha.domain.error;

import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.PlainText;

/** ADR-043: the kernel's free text is text, so it is never an error parameter. */
public record ErrorWithPlainText(PlainText note) implements DomainError {}
