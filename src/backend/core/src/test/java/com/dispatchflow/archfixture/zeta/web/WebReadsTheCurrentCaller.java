package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;
import com.dispatchflow.archfixture.zeta.upper.application.api.UpperCurrentCaller;

/** Rules 14 and 18: a class of {@code web} reads the call's caller, which both allow. */
public final class WebReadsTheCurrentCaller {

    ZetaKernelValue caller() {
        return UpperCurrentCaller.get();
    }
}
