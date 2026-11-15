package com.dispatchflow.archfixture.zeta.lower.application.spi;

import com.dispatchflow.archfixture.zeta.lower.domain.LowerValue;

/** Rule 3: an spi record must not hold a domain type. */
public record UsageFactLeaksTheDomain(LowerValue value) {}
