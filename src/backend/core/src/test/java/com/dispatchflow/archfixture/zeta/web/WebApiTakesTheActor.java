package com.dispatchflow.archfixture.zeta.web;

import com.dispatchflow.archfixture.zeta.gate.application.api.GateActor;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Rule 21: an interface whose mapped method takes the actor, as a generated {@code <Tag>Api}
 * interface's would, with no controller of its own. Nothing implements it, so the contexts that
 * scan the test tree serve nothing from it.
 */
public interface WebApiTakesTheActor {

    @GetMapping("/zeta/act")
    String act(GateActor actor);
}
