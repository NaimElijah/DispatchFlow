package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.stray.StrayValue;

/**
 * Rule 13: {@code web} uses a class of a module missing from the graph, which is neither its {@code
 * api} nor its {@code events}.
 */
public record WebUsesAStrayModule(StrayValue value) {}
