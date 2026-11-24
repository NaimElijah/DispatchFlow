package com.dispatchflow.archfixture.zeta.upper.adapter.out.relay;

import com.dispatchflow.archfixture.zeta.lower.adapter.common.LowerCommon;

/** Rule 10: an adapter uses another module's adapter package that lies outside in and out. */
public record RelayUsesLowersCommon(LowerCommon common) {}
