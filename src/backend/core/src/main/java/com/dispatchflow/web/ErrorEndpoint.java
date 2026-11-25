package com.dispatchflow.web;

import com.dispatchflow.shared.adapter.in.web.Problems;
import jakarta.servlet.RequestDispatcher;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers every error Tomcat dispatches to the error path with the contract's {@code Problem}, so
 * no answer is ever Spring Boot's own error JSON (ADR-140 decision 5, ADR-142). It sees an exception
 * that escaped every exception handler, such as one wrapping a Spring MVC exception whose handler
 * rethrew it, and an error raised outside Spring MVC, such as in a servlet filter. Being an {@code
 * ErrorController}, it replaces Spring Boot's {@code BasicErrorController}, which stands back once
 * one exists, and it takes Boot's own mapping. The trace filter runs on error dispatches too, so the
 * body carries the request's trace ID.
 *
 * <p>The status's code is {@link WebCodes#forStatus}'s, as the contract's handler answers it: a 400
 * names the whole request as its one item, a 503 carries {@code Retry-After}, and a status the
 * contract doesn't list answers 500. A 5xx is logged at ERROR with its exception, and a 4xx at INFO
 * with its status alone. A request for the error path itself, which carries no error status, is a
 * path no operation has: 404 {@code NO_SUCH_OPERATION}, so a client can't write ERROR lines at will.
 */
@RestController
class ErrorEndpoint implements ErrorController {

    private static final Logger log = LoggerFactory.getLogger(ErrorEndpoint.class);
    private static final String STATUS = "status";

    /**
     * Answers one error dispatch. Its parameters are request attributes, never the request, so the
     * call wrapper's rule that no handler reads a stream holds here too ({@code ApplicationRules}
     * rule 16).
     *
     * @param status the status Tomcat records, absent when the error path was requested directly
     * @param exception the exception that escaped, if any
     * @param path the failed request's path, which becomes the problem's {@code instance}, a
     *     {@code [} or {@code ]} in it quoted, as {@link Problems#at} does for every handler
     * @return the problem, as {@code application/problem+json}
     */
    @RequestMapping("${spring.web.error.path:${error.path:/error}}")
    ResponseEntity<ProblemDetail> error(
            @RequestAttribute(name = RequestDispatcher.ERROR_STATUS_CODE, required = false) @Nullable Integer status,
            @RequestAttribute(name = RequestDispatcher.ERROR_EXCEPTION, required = false) @Nullable Throwable exception,
            @RequestAttribute(name = RequestDispatcher.ERROR_REQUEST_URI, required = false) @Nullable String path) {
        HttpStatusCode dispatched = status == null ? HttpStatus.NOT_FOUND : HttpStatusCode.valueOf(status);
        Optional<String> listed = WebCodes.forStatus(dispatched.value());
        HttpStatusCode answered = listed.isPresent() ? dispatched : HttpStatus.INTERNAL_SERVER_ERROR;
        log(dispatched, listed.isPresent(), exception);
        ProblemDetail problem = answered.value() == HttpStatus.BAD_REQUEST.value()
                ? Problems.invalidRequest(List.of(ContractBreaks.wholeRequest()))
                : Problems.of(answered, listed.orElse(WebCodes.UNEXPECTED_ERROR));
        // Left unset, Spring would set the error path itself, which no client asked for.
        if (path != null) {
            Problems.at(problem, path);
        }
        ResponseEntity.BodyBuilder answer =
                ResponseEntity.status(answered).contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (answered.value() == HttpStatus.SERVICE_UNAVAILABLE.value()) {
            answer.header(HttpHeaders.RETRY_AFTER, String.valueOf(Race.RETRY_AFTER_SECONDS));
        }
        return answer.body(problem);
    }

    private static void log(HttpStatusCode dispatched, boolean listed, @Nullable Throwable exception) {
        if (!listed) {
            log.atError()
                    .addKeyValue(STATUS, dispatched.value())
                    .setCause(exception)
                    .log("An error dispatch carried a status the contract doesn't list");
        } else if (dispatched.is5xxServerError()) {
            log.atError()
                    .addKeyValue(STATUS, dispatched.value())
                    .setCause(exception)
                    .log("An error escaped every exception handler");
        } else {
            log.atInfo().addKeyValue(STATUS, dispatched.value()).log("Answered an error dispatch");
        }
    }
}
