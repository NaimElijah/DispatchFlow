package com.dispatchflow.audit.application.api;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import java.util.Objects;
import java.util.Optional;

/**
 * Who made a call or a server step, through which client, as which operation, under which trace
 * (NFR-93, ADR-110): what an audit record says about the attempt beside its time, its records and
 * its outcome. {@code web.security} builds the caller, and {@code web} binds one for every web
 * call, signed in or not, through {@code CurrentCall}; a signed-in call's {@code Actor} carries it
 * too (ADR-140). A job, a listener or a CLI step builds one with {@link #serverStep}.
 *
 * <p>The user and the session are each recorded when known, independently: a refused sign-in
 * typically knows neither (ADR-110).
 *
 * @param user the acting user's account, when known
 * @param session the acting session, when known
 * @param client the app and version the client declared, or empty for a step no client made
 * @param operation the {@code client-api} operation ID, such as {@code publishPlan}, or the server
 *     step's name; Audit checks it is an ASCII identifier of at most 100 characters
 * @param traceId the request's W3C trace-id, the one its log lines carry (NFR-20); Audit checks it
 */
public record CallContext(
        Optional<AccountId> user,
        Optional<SessionId> session,
        Optional<ClientRelease> client,
        String operation,
        String traceId) {

    /** Checks every part is present, possibly empty. */
    public CallContext {
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(client, "client");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(traceId, "traceId");
    }

    /**
     * The context of a step that no client made and no user started, such as a job, a listener or
     * FR-292's written-request step (ADR-110): no user, no session and no client. A job makes its
     * trace-id with {@code TraceIds.fresh()}, as the trace filter does for a request that brought
     * none; a provider callback takes the trace filter's MDC entry (ADR-140 decision 4).
     *
     * @param step the step's name, recorded as the operation; Audit checks it as it checks an
     *     operation ID
     * @param traceId the step's trace-id; Audit checks it
     * @return the context
     * @throws NullPointerException when either is null
     */
    public static CallContext serverStep(String step, String traceId) {
        return new CallContext(Optional.empty(), Optional.empty(), Optional.empty(), step, traceId);
    }
}
