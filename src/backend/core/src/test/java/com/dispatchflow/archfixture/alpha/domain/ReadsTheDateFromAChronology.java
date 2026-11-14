package com.dispatchflow.archfixture.alpha.domain;

import java.time.chrono.ChronoLocalDate;
import java.time.chrono.IsoChronology;

/** Rule 2: dateNow() on a chronology. */
public final class ReadsTheDateFromAChronology {

    static ChronoLocalDate today() {
        return IsoChronology.INSTANCE.dateNow();
    }
}
