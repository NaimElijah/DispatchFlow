package com.dispatchflow.archfixture.alpha.domain.io.locale;

/** Rule 4: String.toUpperCase() with no Locale reads the default locale. */
public final class UpperCasesWithTheDefaultLocale {

    static String upperCase(String text) {
        return text.toUpperCase();
    }
}
