package com.dispatchflow.example.adapter.inbound.web;

import com.dispatchflow.example.api.WidgetRejectedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Example: turns a rejected request into a 400 problem response, for this package's controllers
 * only. A malformed request (a missing field, bad JSON, an id that is not a UUID) is answered by
 * Spring's own problem handling. It runs before the application-wide fallback for unexpected
 * errors, {@code com.dispatchflow.web.UnexpectedErrorHandler}, which would otherwise answer 500.
 */
@RestControllerAdvice(basePackageClasses = WidgetController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class ExampleProblemHandler {

    @ExceptionHandler(WidgetRejectedException.class)
    ProblemDetail rejected(WidgetRejectedException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    }
}
