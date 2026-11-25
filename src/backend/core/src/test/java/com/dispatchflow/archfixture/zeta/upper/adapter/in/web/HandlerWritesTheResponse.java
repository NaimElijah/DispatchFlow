package com.dispatchflow.archfixture.zeta.upper.adapter.in.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Rule 16: a handler method, in a class that is no controller, which takes the servlet response, a
 * subtype of {@code ServletResponse}.
 */
public final class HandlerWritesTheResponse {

    @PostMapping("/zeta/answer")
    void answer(HttpServletResponse response) {
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
