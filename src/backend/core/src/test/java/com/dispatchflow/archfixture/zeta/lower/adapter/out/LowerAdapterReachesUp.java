package com.dispatchflow.archfixture.zeta.lower.adapter.out;

import com.dispatchflow.archfixture.zeta.upper.application.api.UpperFacts;

/** Rule 2: the graph binds every class of a module, its adapters as well as its application. */
public record LowerAdapterReachesUp(UpperFacts upper) {}
