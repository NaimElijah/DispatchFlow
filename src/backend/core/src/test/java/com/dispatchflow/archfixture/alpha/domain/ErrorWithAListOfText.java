package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.DomainError;
import java.util.List;

/** ADR-043: an error parameter carries no personal data or free text. */
public record ErrorWithAListOfText(List<String> reasons) implements DomainError {}
