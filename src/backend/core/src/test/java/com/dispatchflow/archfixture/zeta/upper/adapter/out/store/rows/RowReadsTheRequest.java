package com.dispatchflow.archfixture.zeta.upper.adapter.out.store.rows;

import com.dispatchflow.archfixture.zeta.upper.adapter.in.web.UpperRequest;

/** Rule 9, from below an adapter: a class below the store adapter uses the web adapter. */
public record RowReadsTheRequest(UpperRequest request) {}
