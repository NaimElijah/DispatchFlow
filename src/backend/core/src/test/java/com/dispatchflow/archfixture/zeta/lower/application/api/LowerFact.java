package com.dispatchflow.archfixture.zeta.lower.application.api;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;

/** Rule 3: an api record that holds only kernel types. */
public record LowerFact(ZetaKernelValue value) {}
