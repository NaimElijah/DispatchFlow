package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.lower.domain.LowerValue;

/** Rule 1: the application must not depend on another module's domain. */
public record DependsOnAnotherModulesDomain(LowerValue value) {}
