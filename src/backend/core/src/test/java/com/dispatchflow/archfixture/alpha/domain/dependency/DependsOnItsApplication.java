package com.dispatchflow.archfixture.alpha.domain.dependency;

import com.dispatchflow.archfixture.alpha.application.AlphaUseCase;

/** Rule 1: a domain must not depend on its application layer. */
public record DependsOnItsApplication(AlphaUseCase useCase) {}
