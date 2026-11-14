package com.dispatchflow.archfixture.alpha.domain;

import java.util.Date;

/** Rule 2: java.util.Date. */
public final class UsesALegacyDate {

    static Date now() {
        return new Date();
    }
}
