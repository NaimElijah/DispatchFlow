package com.dispatchflow.archfixture.epsilon.domain;

import com.dispatchflow.archfixture.epsilon.domain.cycle.SubpackageInACycle;
import org.jspecify.annotations.Nullable;

/**
 * ADR-118 rule 7: a domain root type that uses a subpackage, which uses the root back. A slice
 * pattern of {@code ..domain.(**)} leaves the root out, so it would miss this cycle.
 */
public record RootInACycle(@Nullable SubpackageInACycle below) {}
