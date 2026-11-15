package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.upper.adapter.out.UpperStore;

/** Rule 1: the application must not depend on an adapter, even its own module's. */
public record DependsOnItsOwnAdapter(UpperStore store) {}
