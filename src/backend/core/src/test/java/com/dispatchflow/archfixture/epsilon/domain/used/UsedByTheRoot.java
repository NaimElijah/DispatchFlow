package com.dispatchflow.archfixture.epsilon.domain.used;

/**
 * ADR-118 rule 2: a subpackage type that the domain root uses, and that uses nothing of the root.
 */
public record UsedByTheRoot(int count) {}
