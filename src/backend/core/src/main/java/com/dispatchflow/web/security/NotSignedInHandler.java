package com.dispatchflow.web.security;

import com.dispatchflow.shared.adapter.in.web.HandlerOrder;
import com.dispatchflow.shared.adapter.in.web.Problems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers a call with no principal: 401 {@value Problems#ACCESS_TOKEN_REFUSED}, with empty {@code
 * params} and exactly {@code WWW-Authenticate: Bearer}, so a missing, an expired, a malformed and a
 * revoked token look the same (ADR-140 decision 7). Spring MVC hands it the exception the access
 * interceptor throws, through the served JSON converter, as every other problem. It logs the
 * refusal at INFO with its status and code, as the kernel's handler does.
 */
@RestControllerAdvice
@Order(HandlerOrder.KERNEL)
class NotSignedInHandler {

    private static final Logger log = LoggerFactory.getLogger(NotSignedInHandler.class);

    @ExceptionHandler(NotSignedIn.class)
    ResponseEntity<ProblemDetail> notSignedIn(NotSignedIn refused) {
        log.atInfo()
                .addKeyValue("status", HttpStatus.UNAUTHORIZED.value())
                .addKeyValue("code", Problems.ACCESS_TOKEN_REFUSED)
                .log("Refused a call");
        return Problems.unauthorized(Problems.ACCESS_TOKEN_REFUSED);
    }
}
