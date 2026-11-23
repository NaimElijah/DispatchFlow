package com.dispatchflow.archfixture.alpha.domain.io.thread;

import java.util.Timer;

/** Rule 4: a Timer starts a thread of its own. */
public final class SchedulesOnATimer {

    static Timer timer() {
        return new Timer();
    }
}
