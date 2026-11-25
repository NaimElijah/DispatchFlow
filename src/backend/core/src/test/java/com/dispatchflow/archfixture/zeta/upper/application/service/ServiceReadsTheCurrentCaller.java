package com.dispatchflow.archfixture.zeta.upper.application.service;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;
import com.dispatchflow.archfixture.zeta.upper.application.api.UpperCurrentCaller;

/** Rule 14: a use case reads the call's caller instead of taking it as a parameter. */
public final class ServiceReadsTheCurrentCaller {

    ZetaKernelValue caller() {
        return UpperCurrentCaller.get();
    }
}
