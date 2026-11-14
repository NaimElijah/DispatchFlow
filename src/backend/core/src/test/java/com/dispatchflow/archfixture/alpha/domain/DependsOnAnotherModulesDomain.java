package com.dispatchflow.archfixture.alpha.domain;

import com.dispatchflow.archfixture.beta.domain.BetaValue;

/** Rule 1: a domain must not depend on another module's domain. */
public record DependsOnAnotherModulesDomain(BetaValue value) {}
