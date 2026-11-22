package com.dispatchflow.archfixture.alpha.domain.io.locale;

import java.text.MessageFormat;

/** Rule 4: new MessageFormat(String) with no Locale reads the default locale. */
public final class FormatsAMessageWithTheDefaultLocale {

    static String stops(int count) {
        return new MessageFormat("{0} stops").format(new Object[] {count});
    }
}
