package com.dispatchflow.archfixture.zeta.lower.application.api;

import com.dispatchflow.archfixture.zeta.lower.domain.LowerValue;

/** Rule 3: an api record must not hold a domain type, even its own module's. */
public record LowerFactLeaksTheDomain(LowerValue value) {}
