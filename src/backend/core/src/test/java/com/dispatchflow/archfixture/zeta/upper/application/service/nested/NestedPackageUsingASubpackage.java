package com.dispatchflow.archfixture.zeta.upper.application.service.nested;

import com.dispatchflow.archfixture.zeta.upper.application.service.nested.below.UsedByTheNestedPackage;

/**
 * Rule 7: a class of an aggregate's use-case package, below the {@code service} root, that uses the
 * concept package beneath it. The dependency points one way, so the cycle rule allows it; the root
 * rule, which covers every application package that has subpackages (ADR-125), must not.
 */
public record NestedPackageUsingASubpackage(UsedByTheNestedPackage below) {}
