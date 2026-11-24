package com.dispatchflow.archfixture.zeta.upper.adapter.out.mirror.deep;

import com.dispatchflow.archfixture.zeta.lower.adapter.out.cache.deep.LowerEntry;

/** Rule 10, one level down on both sides: a class below an adapter uses one below another
 * module's adapter. */
public record MirrorsADeepEntry(LowerEntry entry) {}
