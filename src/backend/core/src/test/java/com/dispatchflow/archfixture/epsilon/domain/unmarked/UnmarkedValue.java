package com.dispatchflow.archfixture.epsilon.domain.unmarked;

/**
 * ADR-118 rule 11: a type in a domain subpackage that has no {@code package-info}, so the package
 * carries no {@code @NullMarked}.
 */
public record UnmarkedValue(int count) {}
