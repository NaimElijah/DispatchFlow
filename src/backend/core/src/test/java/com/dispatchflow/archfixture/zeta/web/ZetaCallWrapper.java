package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;
import com.dispatchflow.archfixture.zeta.upper.application.api.UpperCurrentCaller;

/**
 * Rule 18: the call wrapper, the one class that binds the call's caller, here from a nested class,
 * as production's binds it from its handler method.
 */
public final class ZetaCallWrapper {

    /** One call's invocation, nested in the wrapper. */
    public static final class Invocation {

        void around(ZetaKernelValue caller, Runnable handler) {
            UpperCurrentCaller.runAs(caller, handler);
        }
    }
}
