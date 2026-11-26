package com.dispatchflow.web.security;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.audit.application.api.ClientKind;
import com.dispatchflow.audit.application.api.ClientRelease;
import com.dispatchflow.shared.adapter.in.web.TraceIds;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/**
 * Who makes a call to a handler under {@code /api/v1}, as the call wrapper binds it (ADR-140
 * decision 4): the call's {@link CallContext}, which {@code CurrentCall} holds, and, when the
 * caller is signed in, the {@link Actor} that {@code CurrentActor} holds on top. The access
 * interceptor builds it from the request's principal and the chosen handler, once the handler is
 * chosen and before its arguments are read, and keeps it in a request attribute for the wrapper.
 * This is the one place that builds an {@code Actor} ({@code ApplicationRules} rule 17).
 *
 * @param call the call's context
 * @param actor the signed-in caller's actor, or empty when the caller isn't signed in
 */
public record Caller(CallContext call, Optional<Actor> actor) {

    /** The header that names the client's app (ADR-082). */
    static final String X_APP = "X-App";

    /** The header that names the client's version (ADR-082). */
    static final String X_APP_VERSION = "X-App-Version";

    /** The MDC key of a signed-in call's account ID, beside the trace ID (ADR-140 decision 8). */
    public static final String ACCOUNT_ID = "accountId";

    /** The MDC key of a signed-in call's session ID, beside the trace ID (ADR-140 decision 8). */
    public static final String SESSION_ID = "sessionId";

    private static final String ATTRIBUTE = Caller.class.getName();
    // Three numbers, as a release is (ADR-110), each short enough that no client text of any size
    // reaches a record; Audit then refuses a leading zero or a number past an int.
    private static final Pattern RELEASE = Pattern.compile("[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}");
    private static final String WEB = "web";
    private static final String PHONE = "phone";

    /**
     * Checks both parts are present, the actor possibly empty, and that an actor carries this very
     * call, so {@code CurrentCall} and {@code CurrentActor} never tell one request two ways.
     */
    public Caller {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(actor, "actor");
        if (actor.isPresent() && !actor.get().call().equals(call)) {
            throw new IllegalArgumentException("the actor carries another call than the caller's");
        }
    }

    /**
     * The caller the access interceptor kept for this request.
     *
     * @param request the request
     * @return the caller, or empty for a handler outside {@code /api/v1}, which the interceptor
     *     doesn't see, and for a signed-in call to a handler that is no annotated handler method
     */
    public static Optional<Caller> of(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof Caller caller ? Optional.of(caller) : Optional.empty();
    }

    /**
     * Builds the caller of a request to a chosen handler.
     *
     * @param request the request, whose {@code X-App} and {@code X-App-Version} name the client
     * @param principal the request's principal, or empty when the caller isn't signed in, whose
     *     record then names no user and no session, since no credential is proven (ADR-120)
     * @param operation the handler's operation ID, which is the generated method's name; Audit
     *     checks it is an ASCII identifier of at most 100 characters
     * @return the caller
     * @throws IllegalStateException when the MDC holds no trace ID, which means the trace filter
     *     didn't run for this request
     */
    static Caller build(HttpServletRequest request, Optional<SignedInCaller> principal, String operation) {
        CallContext call = new CallContext(
                principal.map(SignedInCaller::account),
                principal.map(SignedInCaller::session),
                clientOf(request),
                operation,
                TraceIds.ofTheRunningRequest());
        return new Caller(call, principal.map(signedIn -> new Actor(signedIn.account(), signedIn.roles(), call)));
    }

    /**
     * Keeps this as the request's caller, for the call wrapper, and puts a signed-in caller's account
     * and session IDs in the MDC under {@link #ACCOUNT_ID} and {@link #SESSION_ID}.
     *
     * @param request the request
     */
    void keepFor(HttpServletRequest request) {
        request.setAttribute(ATTRIBUTE, this);
        putInTheMdc();
    }

    /**
     * Puts a signed-in caller's account and session IDs in the MDC under {@link #ACCOUNT_ID} and
     * {@link #SESSION_ID}, and nothing for a caller who isn't signed in. The trace filter clears the
     * MDC when each dispatch ends, so it puts them back on an error or async dispatch of the same
     * request, and the asynchronous listeners' decorator copies them, so every later line of the
     * call says who made it.
     */
    public void putInTheMdc() {
        call.user().ifPresent(account -> MDC.put(ACCOUNT_ID, account.value().toString()));
        call.session().ifPresent(session -> MDC.put(SESSION_ID, session.value().toString()));
    }

    /**
     * The client the request declares: exactly one {@code X-App} that is {@code web} or {@code
     * phone}, with exactly one {@code X-App-Version} of three numbers. Anything else leaves the
     * client out, and is never refused (ADR-110), so no other client text reaches a record.
     */
    static Optional<ClientRelease> clientOf(HttpServletRequest request) {
        List<String> apps = Collections.list(request.getHeaders(X_APP));
        List<String> versions = Collections.list(request.getHeaders(X_APP_VERSION));
        if (apps.size() != 1 || versions.size() != 1) {
            return Optional.empty();
        }
        Optional<ClientKind> kind = switch (apps.getFirst()) {
            case WEB -> Optional.of(ClientKind.WEB);
            case PHONE -> Optional.of(ClientKind.PHONE);
            default -> Optional.empty();
        };
        String version = versions.getFirst();
        return RELEASE.matcher(version).matches() ? kind.map(app -> new ClientRelease(app, version)) : Optional.empty();
    }
}
