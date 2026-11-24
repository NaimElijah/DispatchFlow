package com.dispatchflow.archfixture.zeta.upper.adapter.out.store;

import com.dispatchflow.archfixture.zeta.shared.adapter.out.persistence.ZetaKernelAdapter;

/** Rules 9 and 10 allow it: an adapter uses the persistence adapter every module shares. */
public record StoreSavesThroughTheKernel(ZetaKernelAdapter kernel) {}
