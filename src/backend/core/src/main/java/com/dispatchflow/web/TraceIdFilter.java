package com.dispatchflow.web;

import com.dispatchflow.shared.adapter.in.web.Problems;
import com.dispatchflow.shared.adapter.in.web.TraceIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request its trace ID, before anything else runs (ADR-140 decision 8): the trace-id of
 * a valid {@code traceparent}, or a fresh one. It puts the ID in the MDC under {@link
 * Problems#TRACE_ID}, so every log line and every problem body of the request carries it, and
 * clears the MDC when the request ends. The header itself is never logged: a client can send any
 * text in it.
 *
 * <p>An async or error dispatch of the same request runs it again, and puts back the ID the first
 * pass kept in a request attribute, so the request's every line carries one ID.
 */
@Component
@Order(TraceIdFilter.ORDER)
class TraceIdFilter extends OncePerRequestFilter {

    /**
     * Ahead of Spring Boot's request-context filter (-105) and Spring Security's chain (-100), with
     * room left before it for ADR-071's rate limiter to run after it.
     */
    static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    private static final String TRACEPARENT = "traceparent";
    private static final String KEPT_TRACE_ID = TraceIdFilter.class.getName() + ".traceId";

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        MDC.put(Problems.TRACE_ID, traceIdOf(request));
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private static String traceIdOf(HttpServletRequest request) {
        if (request.getAttribute(KEPT_TRACE_ID) instanceof String kept) {
            return kept;
        }
        String traceId = Traceparent.traceIdOf(Collections.list(request.getHeaders(TRACEPARENT)))
                .orElseGet(TraceIds::fresh);
        request.setAttribute(KEPT_TRACE_ID, traceId);
        return traceId;
    }
}
