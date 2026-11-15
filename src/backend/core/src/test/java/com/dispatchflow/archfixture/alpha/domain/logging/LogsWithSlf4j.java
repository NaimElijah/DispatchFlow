package com.dispatchflow.archfixture.alpha.domain.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Rule 4: SLF4J. */
public final class LogsWithSlf4j {

    static Logger log() {
        return LoggerFactory.getLogger("alpha");
    }
}
