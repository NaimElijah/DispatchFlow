package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;
import com.dispatchflow.archfixture.zeta.upper.application.api.UpperCurrentCaller;

/** Rule 14: {@code web} binds the call's caller around the handler's call. */
public final class WebBindsTheCurrentCaller {

    void around(ZetaKernelValue caller, Runnable handler) {
        UpperCurrentCaller.runAs(caller, handler);
    }
}
