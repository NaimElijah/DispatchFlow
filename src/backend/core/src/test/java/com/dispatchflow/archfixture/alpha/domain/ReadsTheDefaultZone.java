package com.dispatchflow.archfixture.alpha.domain;

import java.time.ZoneId;

/** Rule 2: ZoneId.systemDefault(). */
public final class ReadsTheDefaultZone {

    static ZoneId zone() {
        return ZoneId.systemDefault();
    }
}
