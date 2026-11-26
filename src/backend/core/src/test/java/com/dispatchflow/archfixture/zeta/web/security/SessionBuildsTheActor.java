package com.dispatchflow.archfixture.zeta.web.security;

import com.dispatchflow.archfixture.zeta.gate.application.api.GateActor;

/** Rule 17: {@code web.security} builds the actor from the request's principal, which it allows. */
public final class SessionBuildsTheActor {

    GateActor actor() {
        return new GateActor("dispatcher");
    }
}
