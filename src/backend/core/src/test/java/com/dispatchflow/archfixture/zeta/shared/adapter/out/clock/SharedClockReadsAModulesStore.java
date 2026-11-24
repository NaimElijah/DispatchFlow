package com.dispatchflow.archfixture.zeta.shared.adapter.out.clock;

import com.dispatchflow.archfixture.zeta.upper.adapter.out.UpperStore;

/** Rule 10: a shared adapter uses a module's adapter. */
public record SharedClockReadsAModulesStore(UpperStore store) {}
