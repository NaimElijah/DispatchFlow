package com.dispatchflow.archfixture.zeta.shared;

import com.dispatchflow.archfixture.zeta.shared.application.ZetaClock;

/** Rule 6: the kernel's own package must not depend on the shared application package. */
public record KernelRootUsesTheClock(ZetaClock clock) {}
