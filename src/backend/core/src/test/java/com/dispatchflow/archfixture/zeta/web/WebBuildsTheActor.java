package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.gate.application.api.GateActor;
import java.util.function.Function;

/** Rule 17: a class of {@code web} outside {@code web.security} builds the actor, which it forbids. */
public final class WebBuildsTheActor {

    GateActor actor() {
        return new GateActor("dispatcher");
    }

    Function<String, GateActor> actors() {
        return GateActor::new;
    }
}
