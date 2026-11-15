package com.dispatchflow.archfixture.zeta.lower.application.service;

import com.dispatchflow.archfixture.zeta.upper.application.api.UpperFacts;

/**
 * Rule 2: {@code lower} has no arrow to {@code upper}, so it must not depend on it, even on its
 * api, which rule 1 alone allows.
 */
public record LowerReachesUp(UpperFacts upper) {}
