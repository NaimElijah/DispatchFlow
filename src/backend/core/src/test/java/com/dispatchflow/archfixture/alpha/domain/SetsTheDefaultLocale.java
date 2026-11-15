package com.dispatchflow.archfixture.alpha.domain;

import java.util.Locale;

/** Rule 4: Locale.setDefault changes the whole process's environment. */
public final class SetsTheDefaultLocale {

    static void hebrew() {
        Locale.setDefault(Locale.forLanguageTag("he-IL"));
    }
}
