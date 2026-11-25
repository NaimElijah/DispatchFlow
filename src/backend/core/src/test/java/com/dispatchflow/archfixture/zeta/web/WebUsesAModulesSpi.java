package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.lower.application.spi.LowerUsage;

/**
 * Rule 13: {@code web} uses a module's {@code spi}, which is neither its {@code api} nor its {@code
 * events}.
 */
public record WebUsesAModulesSpi(LowerUsage usage) {}
