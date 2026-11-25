package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rule 16: a controller whose handler reads the body itself, so a retry would read an empty one.
 * It maps no path, so the contexts that scan the test tree serve nothing from it.
 */
@RestController
public final class ControllerTakesAStream {

    String upload(InputStream body) throws IOException {
        return new String(body.readAllBytes(), StandardCharsets.UTF_8);
    }
}
