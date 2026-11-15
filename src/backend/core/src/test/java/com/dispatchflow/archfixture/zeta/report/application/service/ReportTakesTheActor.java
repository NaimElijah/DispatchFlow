package com.dispatchflow.archfixture.zeta.report.application.service;

import com.dispatchflow.archfixture.zeta.gate.application.api.GateActor;

/** Rule 8: a reader may take the actor from the module that names it. */
public final class ReportTakesTheActor {

    void run(GateActor actor) {}
}
