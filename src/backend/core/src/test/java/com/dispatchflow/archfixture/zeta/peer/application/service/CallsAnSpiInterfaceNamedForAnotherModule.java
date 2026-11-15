package com.dispatchflow.archfixture.zeta.peer.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.spi.LowerUsage;

/**
 * Rule 1: {@code peer} implements one interface of {@code lower}'s spi, but the arrow of {@code
 * LowerUsage} names {@code upper}, so {@code peer} must not depend on it.
 */
public record CallsAnSpiInterfaceNamedForAnotherModule(LowerUsage usage) {}
