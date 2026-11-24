package com.dispatchflow.archfixture.zeta.upper.adapter.out.mirror;

import com.dispatchflow.archfixture.zeta.lower.adapter.in.web.LowerForm;

/** Rule 10, into an inbound adapter: an adapter uses another module's web adapter. */
public record MirrorsTheLowerForm(LowerForm form) {}
