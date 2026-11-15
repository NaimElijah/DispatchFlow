package com.dispatchflow.archfixture.alpha.domain.clock;

import java.util.Calendar;

/** Rule 2: java.util.Calendar. */
public final class UsesACalendar {

    static Calendar now() {
        return Calendar.getInstance();
    }
}
