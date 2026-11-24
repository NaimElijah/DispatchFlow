package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import com.dispatchflow.archfixture.zeta.shared.adapter.out.optimizer.ZetaSolverClient;

/** Rule 12: an adapter of a module that solves, but not its optimizer adapter, uses the client. */
public record WebCallsTheSolverClient(ZetaSolverClient client) {}
