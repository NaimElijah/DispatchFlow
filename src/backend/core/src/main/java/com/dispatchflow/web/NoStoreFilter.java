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
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Sets {@code Cache-Control: no-store, no-transform} on every answer under {@code /api/v1}, as the
 * contract's description promises: {@code no-store} so no browser or phone keeps an answer in its
 * HTTP cache, since what an app may store is ADR-076's, and {@code no-transform} so no proxy
 * rewrites an answer or its {@code ETag} (ADR-157).
 *
 * <p>It sets the header before the rest of the chain runs, so a refusal carries it too, whichever
 * filter, interceptor or handler writes it, and the error page Tomcat dispatches to keeps it.
 */
@Component
@Order(FilterOrder.NO_STORE)
class NoStoreFilter extends OncePerRequestFilter {

    static final String NO_STORE_NO_TRANSFORM = "no-store, no-transform";

    private static final UrlPathHelper PATHS = UrlPathHelper.defaultInstance;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = PATHS.getPathWithinApplication(request);
        return !(path.equals(ClientApi.BASE_PATH) || path.startsWith(ClientApi.BASE_PATH + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE_NO_TRANSFORM);
        chain.doFilter(request, response);
    }
}
