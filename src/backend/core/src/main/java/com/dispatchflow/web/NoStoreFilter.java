package com.dispatchflow.web;

import com.dispatchflow.shared.adapter.in.web.ClientApi;
import com.dispatchflow.shared.adapter.in.web.FilterOrder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.RequestPath;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Sets {@code Cache-Control: no-store, no-transform} on every answer under {@code /api}, the root
 * of every version of the client API (ADR-160), as the
 * contract's description promises: {@code no-store} so no browser or phone keeps an answer in its
 * HTTP cache, since what an app may store is ADR-076's, and {@code no-transform} so no proxy
 * rewrites an answer or its {@code ETag} (ADR-157).
 *
 * <p>It sets the header before the rest of the chain runs, so a refusal carries it too, whichever
 * filter, interceptor or handler writes it, and the error page Tomcat dispatches to keeps it.
 *
 * <p>It reads the path as the handler mapping and the access interceptor do, each segment decoded
 * as UTF-8 with its path parameters taken off, so every path they serve under {@code /api} gets the
 * header, however it is spelled, whatever charset the request declares (ADR-160).
 */
@Component
@Order(FilterOrder.NO_STORE)
class NoStoreFilter extends OncePerRequestFilter {

    static final String NO_STORE_NO_TRANSFORM = "no-store, no-transform";

    private static final PathPattern CLIENT_API = PathPatternParser.defaultInstance.parse(ClientApi.ROOT + "/**");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        try {
            RequestPath path = RequestPath.parse(request.getRequestURI(), request.getContextPath());
            return !CLIENT_API.matches(path.pathWithinApplication());
        } catch (IllegalArgumentException unparsable) {
            // A path Spring can't parse reaches no handler, and the trace filter answers it 400;
            // marking that answer never stored costs nothing.
            return false;
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE_NO_TRANSFORM);
        chain.doFilter(request, response);
    }
}
