package com.dispatchflow.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers an exception that nothing else handled with a 500 problem, so every error body matches
 * the client API's {@code Problem} schema. The response says nothing about the cause; the log
 * keeps it.
 *
 * <p>It runs last. Spring Boot's handler for Spring MVC's own exceptions runs at order 0, and each
 * context's handler runs first of all, so this one sees only what they leave.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class UnexpectedErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(UnexpectedErrorHandler.class);

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Unhandled exception while serving a request", exception);
        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
