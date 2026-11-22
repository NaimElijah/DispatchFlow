package com.dispatchflow.archfixture.alpha.domain.io.locale;

import java.text.NumberFormat;

/** Rule 4: NumberFormat.getInstance() with no Locale reads the default locale. */
public final class FormatsANumberWithTheDefaultLocale {

    static String format(double kilometres) {
        return NumberFormat.getInstance().format(kilometres);
    }
}
