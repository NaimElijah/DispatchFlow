package com.dispatchflow.archfixture.alpha.domain.error.personalvalue;

import com.dispatchflow.archfixture.alpha.domain.personal.Contact;
import com.dispatchflow.shared.DomainError;

/** ADR-106: a value that holds personal data, at any depth, is never an error parameter. */
public record ErrorWithAContact(Contact contact) implements DomainError {}
