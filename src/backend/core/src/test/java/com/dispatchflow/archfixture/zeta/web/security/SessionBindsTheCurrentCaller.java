package com.dispatchflow.archfixture.zeta.web.security;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;
import com.dispatchflow.archfixture.zeta.upper.application.api.UpperCurrentCaller;
import java.util.function.BiConsumer;

/**
 * Rule 18: a class of {@code web} other than the call wrapper binds the call's caller, which rule
 * 14 allows and rule 18 forbids.
 */
public final class SessionBindsTheCurrentCaller {

    void around(ZetaKernelValue caller, Runnable handler) {
        UpperCurrentCaller.runAs(caller, handler);
    }

    BiConsumer<ZetaKernelValue, Runnable> binder() {
        return UpperCurrentCaller::runAs;
    }
}
