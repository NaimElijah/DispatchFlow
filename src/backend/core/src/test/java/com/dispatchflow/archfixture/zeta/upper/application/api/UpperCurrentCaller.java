package com.dispatchflow.archfixture.zeta.upper.application.api;

import com.dispatchflow.archfixture.zeta.shared.ZetaKernelValue;

/**
 * Rule 14: a holder of the call's caller, as Access's {@code CurrentActor} is. It reads its own
 * value, which the rule allows the holder itself.
 */
public final class UpperCurrentCaller {

    private static final ScopedValue<ZetaKernelValue> CALLER = ScopedValue.newInstance();

    private UpperCurrentCaller() {}

    /** Runs a call with its caller bound. */
    public static void runAs(ZetaKernelValue caller, Runnable call) {
        ScopedValue.where(CALLER, caller).run(call);
    }

    /** The caller bound around the running call. */
    public static ZetaKernelValue get() {
        return CALLER.get();
    }
}
