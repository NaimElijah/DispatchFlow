package com.dispatchflow.archfixture.alpha.domain.io.locale;

import java.text.DateFormat;

/** Rule 4: DateFormat.getDateInstance() with no Locale reads the default locale. */
public final class FormatsADateWithTheDefaultLocale {

    static DateFormat dateFormat() {
        return DateFormat.getDateInstance();
    }
}
