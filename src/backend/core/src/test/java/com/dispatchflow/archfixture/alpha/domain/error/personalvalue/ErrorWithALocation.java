package com.dispatchflow.archfixture.alpha.domain.error.personalvalue;

import com.dispatchflow.shared.DomainError;
import com.dispatchflow.shared.GeoPoint;

/** ADR-043: an error parameter carries no personal data or free text. */
public record ErrorWithALocation(GeoPoint where) implements DomainError {}
