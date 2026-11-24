package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import com.dispatchflow.archfixture.zeta.lower.adapter.out.cache.LowerCache;

/** Rule 10, from an inbound adapter: it uses another module's adapter, along an arrow rule 2
 * allows. */
public record WebUsesTheLowerCache(LowerCache cache) {}
