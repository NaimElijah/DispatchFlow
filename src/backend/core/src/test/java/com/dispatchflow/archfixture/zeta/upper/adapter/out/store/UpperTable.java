package com.dispatchflow.archfixture.zeta.upper.adapter.out.store;

import com.dispatchflow.archfixture.zeta.shared.adapter.out.persistence.ZetaKernelAdapter;
import com.dispatchflow.archfixture.zeta.upper.adapter.out.store.rows.UpperRow;

/** Rules 9 and 10 allow it: an adapter uses its own subpackage and the adapter every module shares. */
public record UpperTable(UpperRow row, ZetaKernelAdapter kernel) {}
