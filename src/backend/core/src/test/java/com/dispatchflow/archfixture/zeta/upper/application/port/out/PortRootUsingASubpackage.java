package com.dispatchflow.archfixture.zeta.upper.application.port.out;

import com.dispatchflow.archfixture.zeta.upper.application.port.out.used.UsedByThePortRoot;

/** Rule 7: a type of the {@code port.out} root that uses a subpackage, one way. */
public interface PortRootUsingASubpackage {

    /** Reads what the subpackage holds. */
    UsedByThePortRoot read();
}
