package com.dispatchflow.archfixture.zeta.side.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.spi.LowerUsage;

/**
 * Rule 1: {@code side} has an arrow to {@code lower}, but neither owns {@code lower}'s spi nor
 * implements it, so it must not depend on that spi.
 */
public record CallsAnSpiItDoesNotImplement(LowerUsage usage) {}
