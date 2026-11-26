package com.dispatchflow.archfixture.zeta.web.security;

import com.dispatchflow.archfixture.zeta.gate.application.api.GateCall;

/** Rule 17: {@code web.security} builds the call from the request's principal, which it allows. */
public final class SessionBuildsTheCall {

    GateCall call() {
        return new GateCall("dispatcher");
    }
}
