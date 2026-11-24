package com.dispatchflow.archfixture.zeta.lower.adapter.out.optimizer;

import com.dispatchflow.archfixture.zeta.shared.adapter.out.optimizer.ZetaSolverClient;

/** Rule 12: an optimizer adapter of a module that doesn't solve uses the client. */
public record LowerOptimizer(ZetaSolverClient client) {}
