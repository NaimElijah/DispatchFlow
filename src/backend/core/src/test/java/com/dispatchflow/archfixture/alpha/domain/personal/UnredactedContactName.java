package com.dispatchflow.archfixture.alpha.domain.personal;

import com.dispatchflow.shared.PersonalData;

/** ADR-106: a personal record that keeps the implicit toString, which prints its value. */
public record UnredactedContactName(String value) implements PersonalData {}
