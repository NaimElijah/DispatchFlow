package com.dispatchflow.archfixture.alpha.domain;

import java.util.TimeZone;

/** Rule 2: TimeZone.getDefault(). */
public final class ReadsTheDefaultTimeZone {

    static String zone() {
        return TimeZone.getDefault().getID();
    }
}
