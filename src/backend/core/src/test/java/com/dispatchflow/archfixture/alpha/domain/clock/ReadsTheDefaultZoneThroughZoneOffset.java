package com.dispatchflow.archfixture.alpha.domain.clock;

import java.time.ZoneId;
import java.time.ZoneOffset;

/** Rule 2: systemDefault() through a ZoneId subtype. */
public final class ReadsTheDefaultZoneThroughZoneOffset {

    static ZoneId zone() {
        return ZoneOffset.systemDefault();
    }
}
