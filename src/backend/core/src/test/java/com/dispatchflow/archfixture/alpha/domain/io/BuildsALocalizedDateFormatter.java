package com.dispatchflow.archfixture.alpha.domain.io;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

/** Rule 4: DateTimeFormatter.ofLocalizedDate takes its pattern from the default locale. */
public final class BuildsALocalizedDateFormatter {

    static DateTimeFormatter shortDate() {
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT);
    }
}
