package com.dispatchflow.archfixture.alpha.domain;

import java.util.Locale;

/** Rule 4: Locale.getDefault(Category) reads the process's environment. */
public final class ReadsTheDefaultLocaleOfACategory {

    static Locale formatLocale() {
        return Locale.getDefault(Locale.Category.FORMAT);
    }
}
