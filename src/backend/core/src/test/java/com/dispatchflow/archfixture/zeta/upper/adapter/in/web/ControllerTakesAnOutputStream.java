package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import java.io.IOException;
import java.io.OutputStream;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rule 16: a controller whose handler writes the response's bytes itself, which a retry would
 * write twice. It maps no path, so the contexts that scan the test tree serve nothing from it.
 */
@RestController
public final class ControllerTakesAnOutputStream {

    void download(OutputStream body) throws IOException {
        body.write(0);
    }
}
