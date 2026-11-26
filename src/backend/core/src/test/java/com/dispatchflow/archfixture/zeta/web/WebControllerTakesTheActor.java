package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.gate.application.api.GateActor;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rule 21: a controller whose handler takes the actor, which Spring would build from the request's
 * parameters. It maps no path, so the contexts that scan the test tree serve nothing from it.
 */
@RestController
public final class WebControllerTakesTheActor {

    String act(GateActor actor) {
        return actor.name();
    }
}
