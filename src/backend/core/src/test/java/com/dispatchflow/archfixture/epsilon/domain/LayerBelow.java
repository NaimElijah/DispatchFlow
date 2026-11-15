package com.dispatchflow.archfixture.epsilon.domain;

/** ADR-118 rule 7: a domain root type that a subpackage uses, and that uses no subpackage. */
public record LayerBelow(int count) {}
