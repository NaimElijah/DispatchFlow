package com.dispatchflow.audit.application.api;

import org.jspecify.annotations.Nullable;

/**
 * The {@link CallContext} of the web call running on this thread, which a controller passes to a
 * use case that takes one (ADR-140 decision 4). {@code web}'s call wrapper binds it for every call
 * to a handler under {@code /api/v1}, signed in or not, and the binding ends with the call. A bound
 * value can't be changed during the call, because it is a {@link ScopedValue}.
 *
 * <p>Only {@code web} and the modules' {@code adapter.in.web} read it ({@code ApplicationRules}
 * rule 14), and only the call wrapper binds it (rule 18). An exception handler never reads it:
 * Spring MVC runs one after the call's scope has ended. A job, a listener or a CLI step has no web
 * call, so it builds its own context with {@link CallContext#serverStep}.
 */
public final class CurrentCall {

    private static final ScopedValue<CallContext> CALL = ScopedValue.newInstance();

    private CurrentCall() {}

    /**
     * The context bound around the running call.
     *
     * @return the context
     * @throws IllegalStateException when no call is bound, as outside a handler under {@code
     *     /api/v1}
     */
    public static CallContext get() {
        if (!CALL.isBound()) {
            throw new IllegalStateException("No call context is bound: this code runs outside a web call");
        }
        return CALL.get();
    }

    /**
     * Runs an operation with a call's context bound, and unbinds it when the operation returns or
     * throws.
     *
     * @param call the call's context
     * @param operation what runs with it bound
     * @param <R> what the operation returns
     * @param <X> what the operation may throw
     * @return what the operation returned
     * @throws X what the operation threw
     */
    public static <R extends @Nullable Object, X extends Throwable> R callWith(
            CallContext call, ScopedValue.CallableOp<? extends R, X> operation) throws X {
        return ScopedValue.where(CALL, call).call(operation);
    }
}
