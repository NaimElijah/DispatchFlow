package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.lower.domain.LowerValue;

/**
 * Rule 13: {@code web} uses a module's domain type, which is neither its {@code api} nor its {@code
 * events}.
 */
public record WebReadsAModulesDomain(LowerValue value) {}
