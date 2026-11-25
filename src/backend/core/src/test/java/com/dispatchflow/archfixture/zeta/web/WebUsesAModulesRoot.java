package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.lower.LowerRootValue;

/**
 * Rule 13: {@code web} uses a class at a module's root package, which is neither its {@code api}
 * nor its {@code events}.
 */
public record WebUsesAModulesRoot(LowerRootValue value) {}
