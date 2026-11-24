package com.dispatchflow.archfixture.alpha.domain.mapping;

import com.dispatchflow.archfixture.shared.adapter.out.clock.SystemClockReadsTheClock;

/**
 * Rule 1: a domain uses no shared adapter, the clock's included, so it can't read the time through
 * one (ADR-130 decision 5).
 *
 * @param clock the adapter it must not hold
 */
public record UsesTheSharedClock(SystemClockReadsTheClock clock) {}
