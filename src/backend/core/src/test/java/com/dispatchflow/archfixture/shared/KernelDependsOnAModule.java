package com.dispatchflow.archfixture.shared;

import com.dispatchflow.archfixture.alpha.domain.AlphaValue;

/** Rule 1: the kernel must not depend on a module. */
public record KernelDependsOnAModule(AlphaValue value) {}
