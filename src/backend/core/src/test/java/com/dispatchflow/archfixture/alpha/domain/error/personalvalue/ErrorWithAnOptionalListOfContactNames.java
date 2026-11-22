package com.dispatchflow.archfixture.alpha.domain.error.personalvalue;

import com.dispatchflow.archfixture.alpha.domain.personal.ContactName;
import com.dispatchflow.shared.DomainError;
import java.util.List;
import java.util.Optional;

/** ADR-106: a personal value two type arguments deep is still personal data. */
public record ErrorWithAnOptionalListOfContactNames(Optional<List<ContactName>> contacts) implements DomainError {}
