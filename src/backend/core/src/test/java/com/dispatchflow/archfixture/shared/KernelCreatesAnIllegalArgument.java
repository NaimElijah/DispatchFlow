package com.dispatchflow.archfixture.shared;

/** Rule 5 covers the kernel too. */
public final class KernelCreatesAnIllegalArgument {

    static void refuse() {
        throw new IllegalArgumentException("refused");
    }
}
