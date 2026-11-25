package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.UpperRequest;

/**
 * Rule 13: {@code web} uses a module's adapter, which is neither its {@code api} nor its {@code
 * events}.
 */
public record WebUsesAModulesAdapter(UpperRequest request) {}
