package com.dispatchflow.archfixture.zeta.lower.domain;

import com.dispatchflow.archfixture.zeta.shared.application.ZetaClock;

/** Rule 6: a domain must not depend on the shared application package. */
public record LowerDomainUsesTheClock(ZetaClock clock) {}
