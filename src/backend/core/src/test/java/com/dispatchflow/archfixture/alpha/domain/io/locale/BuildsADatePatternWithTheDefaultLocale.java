package com.dispatchflow.archfixture.alpha.domain.io.locale;

import java.time.format.DateTimeFormatter;

/** Rule 4: DateTimeFormatter.ofPattern(String) with no Locale reads the default locale. */
public final class BuildsADatePatternWithTheDefaultLocale {

    static DateTimeFormatter dayAndMonth() {
        return DateTimeFormatter.ofPattern("d MMM");
    }
}
