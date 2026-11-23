package com.dispatchflow.archfixture.alpha.domain.io.locale;

/** Rule 4: String.formatted takes no Locale, so it always reads the default one. */
public final class FormatsATemplateWithTheDefaultLocale {

    static String format(double kilometres) {
        return "%.1f km".formatted(kilometres);
    }
}
