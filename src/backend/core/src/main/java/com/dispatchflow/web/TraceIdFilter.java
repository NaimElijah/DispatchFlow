package com.dispatchflow.web;

import com.dispatchflow.shared.adapter.in.web.Problems;
import com.dispatchflow.shared.adapter.in.web.TraceIds;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.apache.coyote.BadRequestException;
import org.apache.tomcat.util.http.InvalidParameterException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.RequestPath;
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
 *
 * <p>An exception that escapes every exception handler on a request or async dispatch is handed to
 * Tomcat's error page here, while the MDC still holds the ID, as Spring Boot's {@code
 * ErrorPageFilter} does for a war: the exception, its {@code ServletException} wrappers taken off,
 * goes in {@link RequestDispatcher#ERROR_EXCEPTION}, and {@code sendError(500)} makes Tomcat
 * dispatch it to {@code ErrorEndpoint}, which answers it as the contract's {@code Problem} and logs
 * it once, with the ID (ADR-142 decision 5). Left to escape, Tomcat would log it a second time,
 * with no ID, after this filter cleared the MDC.
 *
 * <p>On a request dispatch, a path Spring can't parse, which only a malformed escape in a path
 * parameter can be, gets {@code sendError(400)} before the chain runs, with no exception in the
 * request, so no handler sees it.
 *
 * <p>Tomcat's own refusals of the request keep their quiet answer, as Tomcat gives them when they
 * escape the servlet: an {@code InvalidParameterException} asks for its error code, and a {@code
 * BadRequestException}, which a {@code ClientAbortException} is, for 400. Neither is put in the
 * request, since the first's message quotes a parameter's value, so {@code ErrorEndpoint} logs the
 * 4xx at INFO with its status alone.
 *
 * <p>Once the response is committed, its status can't change, so the exception escapes as before
 * and Tomcat cuts the connection, which tells the client its body is incomplete; one escaping an
 * error dispatch escapes too, since Tomcat dispatches an error page only once. When Tomcat no
 * longer allows I/O on the connection, as after the client left, it runs no error dispatch, so the
 * exception goes unlogged: accepted, since no one is left to answer.
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
    // The dispatches whose escaped exception becomes Tomcat's error dispatch; an error, include or
    // forward dispatch is already inside one.
    private static final Set<DispatcherType> HANDED_TO_THE_ERROR_PAGE =
            EnumSet.of(DispatcherType.REQUEST, DispatcherType.ASYNC);

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
            if (request.getDispatcherType() == DispatcherType.REQUEST && !hasAPathSpringCanParse(request)) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST);
                return;
            }
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException escaped) {
            if (!HANDED_TO_THE_ERROR_PAGE.contains(request.getDispatcherType()) || response.isCommitted()) {
                throw escaped;
            }
            handToTheErrorPage(request, response, unwrapped(escaped));
        } finally {
            MDC.clear();
        }
    }

    private static void handToTheErrorPage(HttpServletRequest request, HttpServletResponse response, Throwable cause)
            throws IOException {
        switch (cause) {
            case InvalidParameterException refused -> response.sendError(refused.getErrorCode());
            case BadRequestException refused -> response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            default -> {
                request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, cause);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        }
    }

    /**
     * Whether Spring can parse the request's path, as it will before it routes the request. Spring
     * parses it in {@code DispatcherServlet.doService}, outside its exception handling, and Tomcat
     * passes a path parameter's escape undecoded, so a malformed one, as in {@code ;a=%zz}, would
     * escape as a 500 and an ERROR line that any client could write at will (ADR-142 decision 5).
     * The parse caches nothing; Spring parses the path again itself.
     */
    private static boolean hasAPathSpringCanParse(HttpServletRequest request) {
        try {
            RequestPath.parse(request.getRequestURI(), request.getContextPath());
            return true;
        } catch (IllegalArgumentException malformed) {
            // A refusal, not a failure: the 400 answers it, and no line names the path.
            return false;
        }
    }

    /**
     * The exception under Spring's {@code ServletException} wrappers. Tomcat would put the outer
     * wrapper in the request, so they are taken off here, and {@code ErrorEndpoint} logs the cause
     * itself.
     */
    private static Throwable unwrapped(Exception escaped) {
        Throwable cause = escaped;
        while (cause instanceof ServletException wrapper && wrapper.getRootCause() != null) {
            cause = wrapper.getRootCause();
        }
        return cause;
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
