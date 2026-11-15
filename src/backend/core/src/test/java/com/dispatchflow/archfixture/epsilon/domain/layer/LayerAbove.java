package com.dispatchflow.archfixture.epsilon.domain.layer;

import com.dispatchflow.archfixture.epsilon.domain.LayerBelow;

/** ADR-118 rule 7: a subpackage type that uses the domain root, one way. */
public record LayerAbove(LayerBelow below) {}
