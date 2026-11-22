package com.dispatchflow.archfixture.zeta.upper.application.port.out.nested;

import com.dispatchflow.archfixture.zeta.upper.application.port.out.nested.below.UsedByTheNestedPort;

/**
 * Rule 7: a port of an aggregate's port package, below the {@code port.out} root, that uses the
 * concept package beneath it. The dependency points one way, so the cycle rule allows it; the rule
 * for every application package (ADR-125) must not.
 */
public interface NestedPortUsingASubpackage {

    /** Reads what the package beneath holds. */
    UsedByTheNestedPort read();
}
