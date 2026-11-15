package com.dispatchflow.archfixture.alpha.domain.io;

import java.text.DecimalFormat;

/** Rule 4: new DecimalFormat(String) with no symbols reads the default locale. */
public final class BuildsADecimalFormatWithTheDefaultLocale {

    static String format(double kilometres) {
        return new DecimalFormat("0.0").format(kilometres);
    }
}
