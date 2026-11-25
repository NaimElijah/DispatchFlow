package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import org.springframework.web.bind.annotation.RestController;

/** Rule 16: a controller whose handler takes what a converter read, which a retry can reuse. */
@RestController
public final class ControllerTakesItsArguments {

    String create(UpperRequest request) {
        return request.body();
    }
}
