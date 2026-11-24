package com.dispatchflow.archfixture.zeta.shared.adapter.out.clock;

import com.dispatchflow.archfixture.zeta.lower.domain.LowerValue;

/** Rule 11: a shared adapter uses a module's domain. */
public record ClockReadsAModulesDomain(LowerValue value) {}
