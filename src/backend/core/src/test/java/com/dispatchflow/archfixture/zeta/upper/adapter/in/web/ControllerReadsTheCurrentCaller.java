package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;
import com.dispatchflow.archfixture.zeta.upper.application.api.UpperCurrentCaller;

/** Rule 14: a module's controller reads the call's caller to pass it to the use case. */
public final class ControllerReadsTheCurrentCaller {

    ZetaKernelValue caller() {
        return UpperCurrentCaller.get();
    }
}
