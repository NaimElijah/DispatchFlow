package com.dispatchflow.archfixture.zeta.web.securityx;

import com.dispatchflow.archfixture.zeta.gate.application.api.GateCall;

/**
 * Rule 17: a package whose name only begins with {@code web.security}'s, which the rule must not
 * take for it.
 */
public final class SecurityxBuildsTheCall {

    GateCall call() {
        return new GateCall("dispatcher");
    }
}
