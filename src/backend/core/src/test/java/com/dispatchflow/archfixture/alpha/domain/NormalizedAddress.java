package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.shared.PersonalData;

/** Stands in for Orders' NormalizedAddress, which is marked as personal data. */
public record NormalizedAddress(String value) implements PersonalData {}
