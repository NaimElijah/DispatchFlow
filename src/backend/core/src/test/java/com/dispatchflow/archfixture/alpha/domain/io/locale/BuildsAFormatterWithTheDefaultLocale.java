package com.dispatchflow.archfixture.alpha.domain.io.locale;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;

/** Rule 4: DateTimeFormatterBuilder.toFormatter() with no Locale reads the default locale. */
public final class BuildsAFormatterWithTheDefaultLocale {

    static DateTimeFormatter dayAndMonth() {
        return new DateTimeFormatterBuilder().appendPattern("d MMM").toFormatter();
    }
}
