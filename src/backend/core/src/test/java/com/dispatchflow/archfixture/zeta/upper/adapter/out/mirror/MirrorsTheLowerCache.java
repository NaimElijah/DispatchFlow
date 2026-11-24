package com.dispatchflow.archfixture.zeta.upper.adapter.out.mirror;

import com.dispatchflow.archfixture.zeta.lower.adapter.out.cache.LowerCache;

/** Rule 10: an adapter uses another module's adapter, along an arrow rule 2 allows. */
public record MirrorsTheLowerCache(LowerCache cache) {}
