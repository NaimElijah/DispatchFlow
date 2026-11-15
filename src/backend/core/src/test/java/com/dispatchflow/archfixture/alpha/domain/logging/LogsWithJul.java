package com.dispatchflow.archfixture.alpha.domain.logging;

import java.util.logging.Logger;

/** Rule 4: java.util.logging. */
public final class LogsWithJul {

    static Logger log() {
        return Logger.getLogger("alpha");
    }
}
