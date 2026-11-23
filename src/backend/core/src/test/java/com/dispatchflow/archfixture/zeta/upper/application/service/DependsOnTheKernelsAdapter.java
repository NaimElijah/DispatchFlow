package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.shared.adapter.out.persistence.ZetaKernelAdapter;

/** Rule 1: the application must not depend on the kernel's shared adapter either (ADR-126). */
public record DependsOnTheKernelsAdapter(ZetaKernelAdapter adapter) {}
