package com.dispatchflow.archfixture.alpha.domain.io;

import java.lang.invoke.MethodHandles;

/** Rule 4: MethodHandles reaches a member by name, as reflection does. */
public final class LooksUpAMethodHandle {

    static MethodHandles.Lookup lookup() {
        return MethodHandles.lookup();
    }
}
