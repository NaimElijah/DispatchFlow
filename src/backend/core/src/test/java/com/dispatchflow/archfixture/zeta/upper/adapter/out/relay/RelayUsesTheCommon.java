package com.dispatchflow.archfixture.zeta.upper.adapter.out.relay;

import com.dispatchflow.archfixture.zeta.upper.adapter.common.UpperCommon;

/** Rule 9: an adapter uses a package of its module's adapter that lies outside in and out. */
public record RelayUsesTheCommon(UpperCommon common) {}
