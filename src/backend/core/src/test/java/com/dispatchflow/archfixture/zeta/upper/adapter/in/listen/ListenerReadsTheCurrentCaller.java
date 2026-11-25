package com.dispatchflow.archfixture.zeta.upper.adapter.in.listen;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;
import com.dispatchflow.archfixture.zeta.upper.application.api.UpperCurrentCaller;

/** Rule 14: a listener, which no web call starts, reads the call's caller. */
public final class ListenerReadsTheCurrentCaller {

    ZetaKernelValue caller() {
        return UpperCurrentCaller.get();
    }
}
