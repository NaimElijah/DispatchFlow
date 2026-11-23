package com.dispatchflow.archfixture.alpha.domain.io.process;

import java.lang.management.ManagementFactory;

/** Rule 4: java.lang.management reads the running process. */
public final class ReadsTheJvmsManagement {

    static long uptime() {
        return ManagementFactory.getRuntimeMXBean().getUptime();
    }
}
