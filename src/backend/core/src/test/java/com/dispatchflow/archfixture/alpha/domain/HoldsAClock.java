package com.dispatchflow.archfixture.alpha.domain;

import java.time.Clock;

/** Rule 2: a java.time.Clock in the domain. */
public record HoldsAClock(Clock clock) {}
