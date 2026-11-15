package com.dispatchflow.archfixture.epsilon.domain.cycle;

import com.dispatchflow.archfixture.epsilon.domain.RootInACycle;
import org.jspecify.annotations.Nullable;

/** ADR-118 rule 7: the subpackage half of a cycle with the domain root. */
public record SubpackageInACycle(@Nullable RootInACycle above) {}
