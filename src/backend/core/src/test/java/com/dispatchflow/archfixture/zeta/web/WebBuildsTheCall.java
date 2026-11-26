package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.gate.application.api.GateCall;
import java.util.function.Function;

/**
 * Rule 17: a class of {@code web} outside {@code web.security} builds a call that names a caller,
 * which it forbids.
 */
public final class WebBuildsTheCall {

    GateCall call() {
        return new GateCall("dispatcher");
    }

    Function<String, GateCall> calls() {
        return GateCall::new;
    }
}
