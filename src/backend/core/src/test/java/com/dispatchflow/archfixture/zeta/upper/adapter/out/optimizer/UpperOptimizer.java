package com.dispatchflow.archfixture.zeta.upper.adapter.out.optimizer;

import com.dispatchflow.archfixture.zeta.shared.adapter.out.optimizer.ZetaSolverClient;

/** Rule 12 allows it: the optimizer adapter of a module that solves wraps the client. */
public record UpperOptimizer(ZetaSolverClient client) {}
