package com.dispatchflow.archfixture.zeta.report.application.service;

import com.dispatchflow.archfixture.zeta.lower.events.LowerHappened;

/** Rule 8: a reader may hear a source module's events. */
public final class ReportHearsTheEvent {

    void on(LowerHappened event) {}
}
