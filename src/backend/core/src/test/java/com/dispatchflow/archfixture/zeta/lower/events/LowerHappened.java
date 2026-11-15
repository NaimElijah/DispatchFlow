package com.dispatchflow.archfixture.zeta.lower.events;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;

/** An event of the module {@code lower}, which other modules' application layers may hear. */
public record LowerHappened(ZetaKernelValue value) {}
