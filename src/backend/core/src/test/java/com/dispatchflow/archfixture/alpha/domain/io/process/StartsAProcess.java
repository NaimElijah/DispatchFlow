package com.dispatchflow.archfixture.alpha.domain.io.process;

/** Rule 4: processes. */
public final class StartsAProcess {

    static ProcessBuilder process() {
        return new ProcessBuilder("ls");
    }
}
