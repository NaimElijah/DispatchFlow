package com.dispatchflow.archfixture.zeta.upper.adapter.in.web.form;

import com.dispatchflow.archfixture.zeta.upper.adapter.out.store.rows.UpperRow;

/** Rule 9, one level down on both sides: a class below the web adapter uses one below the store
 * adapter. */
public record FormUsesTheRows(UpperRow row) {}
