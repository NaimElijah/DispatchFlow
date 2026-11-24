package com.dispatchflow.archfixture.zeta.upper.adapter.out.store;

import com.dispatchflow.archfixture.zeta.upper.adapter.out.store.rows.UpperRow;

/** Rule 9 allows it: an adapter uses its own subpackage. */
public record UpperTable(UpperRow row) {}
