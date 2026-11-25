package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.InputStreamResource;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rule 16: a controller whose handler takes the body as an {@code InputStreamResource}, which Spring's
 * resource converter hands over as a live stream, so a retry would find it already read. It maps no
 * path, so the contexts that scan the test tree serve nothing from it.
 */
@RestController
public final class ControllerTakesAStreamedResource {

    String upload(@RequestBody InputStreamResource body) throws IOException {
        return new String(body.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
