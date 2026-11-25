package com.dispatchflow.web;

import com.dispatchflow.shared.adapter.in.web.HandlerOrder;
import com.dispatchflow.shared.adapter.in.web.Problems;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Answers an exception that nothing else handled with a 500 problem, code {@code
 * UNEXPECTED_ERROR}, so every error body matches the client API's {@code Problem} schema. The
 * response says nothing about the cause, and carries the trace ID that the log line carries too.
 *
 * <p>It runs last ({@link HandlerOrder#UNEXPECTED}). Spring asks it only when no earlier handler
 * matches the exception or any of its causes: the first that matches answers, and when that one
 * rethrows, as the contract's handler does for a type it doesn't know that wraps one of Spring
 * MVC's, this one is never asked and {@code ErrorEndpoint} answers instead. Spring Boot's own
 * handler of Spring MVC's exceptions no longer runs: the contract's handler replaces it.
 */
@RestControllerAdvice
@Order(HandlerOrder.UNEXPECTED)
class UnexpectedErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(UnexpectedErrorHandler.class);

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception while serving a request", exception);
        return Problems.at(
                Problems.of(HttpStatus.INTERNAL_SERVER_ERROR, WebCodes.UNEXPECTED_ERROR), request.getRequestURI());
    }
}
