package com.dispatchflow.web.security;

import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.adapter.in.web.Problems;
import com.dispatchflow.shared.application.Clock;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Checks the caller of every handler under {@code /api/v1}, once the handler is chosen and before
 * its arguments are read (ADR-140 decisions 3, 4 and 9). It builds the call's {@link Caller} from
 * the request's principal and the handler, and keeps it for the call wrapper, which binds the
 * call's holders from it. A call with no principal answers 401 {@value
 * Problems#ACCESS_TOKEN_REFUSED}, with exactly {@code WWW-Authenticate: Bearer}, through {@link
 * NotSignedIn}; a changing call first writes its refused audit record, which names the operation
 * and no user or session, since no credential is proven (ADR-120). A refused read leaves none.
 *
 * <p>No operation is open yet: #199 names the contract's open marker, and an open operation then
 * lets a caller without a principal through, ignoring any {@code Authorization} header. A handler
 * that isn't an annotated handler method, such as a router function, an {@code HttpRequestHandler}
 * bean or the static-resource handler of a path no operation has, is no operation, so it is never
 * open: with no principal it answers the same 401, with no audit record, since it names no
 * operation (ADR-140 decision 3, ADR-148; {@link NotSignedIn} says how each type is answered). With
 * a principal it passes, with no caller kept, until #171 (stretch) adds the role check's 403 here
 * and the rest of the default refusal. A CORS preflight passes.
 */
@Component
class AccessInterceptor implements HandlerInterceptor {

    private final AuditLog auditLog;
    private final Clock clock;

    /**
     * Creates the interceptor.
     *
     * @param auditLog where a refused changing call's record is written
     * @param clock the time each refused record carries
     */
    AccessInterceptor(AuditLog auditLog, Clock clock) {
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * Lets a signed-in call through, with its caller kept when its handler is an annotated handler
     * method, and refuses any other.
     *
     * @throws NotSignedIn when the call has no principal
     * @throws IllegalStateException when the MDC holds no trace ID, which means the trace filter
     *     didn't run
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (CorsUtils.isPreFlightRequest(request)) {
            return true;
        }
        Optional<SignedInCaller> principal = SignedInCaller.of(request);
        if (!(handler instanceof HandlerMethod method)) {
            if (principal.isEmpty()) {
                throw new NotSignedIn();
            }
            return true;
        }
        Caller caller = Caller.build(request, principal, method.getMethod().getName());
        if (principal.isEmpty()) {
            if (Caller.mayChange(request)) {
                auditLog.refused(caller.call(), clock.now(), List.of(), Problems.ACCESS_TOKEN_REFUSED);
            }
            throw new NotSignedIn();
        }
        caller.keepFor(request);
        return true;
    }
}
