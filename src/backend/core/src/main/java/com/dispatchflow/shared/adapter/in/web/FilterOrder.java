package com.dispatchflow.shared.adapter.in.web;

import org.springframework.core.Ordered;

/**
 * The order of the core's own servlet filters, for their {@code @Order}: the trace filter, then
 * whatever sets the request's principal (ADR-140 decisions 4 and 8, ADR-147). Both run before
 * Spring Boot's request-context filter, at -105, and Spring Security's chain, at -100. The room
 * between the two is ADR-071's rate limiter's, which ADR-140 decision 9 puts after the trace
 * filter, so its refusals are traced, and before the principal's filter.
 *
 * <p>It sits in the kernel's adapter, beside {@link HandlerOrder}, so {@code web} and {@code
 * web.security} each take their order from here, and {@code web.security} needn't depend on {@code
 * web} to come after its trace filter.
 */
public final class FilterOrder {

    /** {@code web}'s trace filter, which every other filter's log lines and failures rely on. */
    public static final int TRACE_ID = Ordered.HIGHEST_PRECEDENCE + 10;

    /**
     * The filter that sets the request's principal: the stand-in session now, the token filter
     * after sign-in (#199). It comes after the trace filter, so a failure in it is traced.
     */
    public static final int PRINCIPAL = TRACE_ID + 10;

    private FilterOrder() {}
}
