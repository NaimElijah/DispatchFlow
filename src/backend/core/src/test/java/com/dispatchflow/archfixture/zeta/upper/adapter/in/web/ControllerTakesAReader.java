package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import java.io.IOException;
import java.io.Reader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rule 16: a controller whose handler reads the body as characters itself, so a retry would read
 * an empty one. It maps no path, so the contexts that scan the test tree serve nothing from it.
 */
@RestController
public final class ControllerTakesAReader {

    int upload(Reader body) throws IOException {
        return body.read();
    }
}
