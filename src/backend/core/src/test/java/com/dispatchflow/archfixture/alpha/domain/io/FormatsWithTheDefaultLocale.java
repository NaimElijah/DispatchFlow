package com.dispatchflow.archfixture.alpha.domain.io;

/** Rule 4: String.format(String, ...) with no Locale reads the default locale. */
public final class FormatsWithTheDefaultLocale {

    static String format(double kilometres) {
        return String.format("%.1f km", kilometres);
    }
}
