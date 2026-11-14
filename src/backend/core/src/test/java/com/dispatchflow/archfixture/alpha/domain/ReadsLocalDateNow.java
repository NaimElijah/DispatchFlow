package com.dispatchflow.archfixture.alpha.domain;

import java.time.LocalDate;

/** Rule 2: LocalDate.now(). */
public final class ReadsLocalDateNow {

    static LocalDate today() {
        return LocalDate.now();
    }
}
