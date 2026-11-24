package com.dispatchflow.archfixture.zeta.shared.adapter.out.clock;

import com.dispatchflow.archfixture.zeta.lower.events.LowerHappened;

/** Rule 11: a shared adapter uses a module's event. */
public record ClockHearsAModulesEvent(LowerHappened event) {}
