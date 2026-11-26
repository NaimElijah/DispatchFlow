package com.dispatchflow.archfixture.zeta.web.security;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;
import com.dispatchflow.archfixture.zeta.upper.application.api.UpperCurrentCaller;

/**
 * Rule 18: a class other than the call wrapper binds the call's caller through an overload of the
 * holder's read, which the rule counts as a binding, since only {@code get()} with no parameter
 * reads.
 */
public final class SessionBindsThroughTheRead {

    void around(ZetaKernelValue caller, Runnable handler) {
        UpperCurrentCaller.get(caller, handler);
    }
}
