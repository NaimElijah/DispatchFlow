package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rule 16: a controller whose handler takes the servlet request, a subtype of {@code
 * ServletRequest}, and so can read the body past the cap. It maps no path, so the contexts that
 * scan the test tree serve nothing from it.
 */
@RestController
public final class ControllerTakesTheRequest {

    int upload(HttpServletRequest request) throws IOException {
        return request.getInputStream().read();
    }
}
