package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.gate.application.api.GateActor;
import java.util.Optional;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rule 21: a controller whose handlers take the actor, which Spring would build from the request's
 * parameters: as the parameter itself, inside a type argument, and as an array. It maps no path, so
 * the contexts that scan the test tree serve nothing from it.
 */
@RestController
public final class WebControllerTakesTheActor {

    String act(GateActor actor) {
        return actor.name();
    }

    String actIfSignedIn(Optional<GateActor> actor) {
        return actor.map(GateActor::name).orElse("");
    }

    String actForAll(GateActor[] actors) {
        return String.valueOf(actors.length);
    }
}
