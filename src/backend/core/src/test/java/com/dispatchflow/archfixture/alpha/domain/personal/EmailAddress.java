package com.dispatchflow.archfixture.alpha.domain.personal;

import com.dispatchflow.shared.PersonalData;

/** Stands in for Access's EmailAddress, which is marked as personal data. */
public record EmailAddress(String value) implements PersonalData {}
