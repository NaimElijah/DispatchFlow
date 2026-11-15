package com.dispatchflow.archfixture.zeta.lower.events;

import com.dispatchflow.archfixture.zeta.shared.application.ZetaClock;

/** Rule 6: an events package must not depend on the shared application package. */
public record LowerEventUsesTheClock(ZetaClock clock) {}
