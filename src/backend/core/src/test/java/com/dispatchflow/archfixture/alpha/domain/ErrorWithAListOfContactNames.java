package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;
import java.util.List;

/** ADR-043: a list of personal values is personal data. */
public record ErrorWithAListOfContactNames(List<ContactName> contacts) implements DomainError {}
