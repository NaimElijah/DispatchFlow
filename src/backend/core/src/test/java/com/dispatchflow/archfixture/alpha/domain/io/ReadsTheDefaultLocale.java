package com.dispatchflow.archfixture.alpha.domain.io;

import java.util.Locale;

/** Rule 4: Locale.getDefault() reads the process's environment. */
public final class ReadsTheDefaultLocale {

    static Locale locale() {
        return Locale.getDefault();
    }
}
