package com.dispatchflow.access.application.api;

import org.jspecify.annotations.Nullable;

/**
 * The {@link Actor} of the signed-in web call running on this thread, which a controller passes
 * first to a web-called use case (ADR-140 decision 4). {@code web}'s call wrapper binds it, on top
 * of {@code CurrentCall}, when the call's caller is signed in, and the binding ends with the call.
 * A bound value can't be changed during the call, because it is a {@link ScopedValue}.
 *
 * <p>Only {@code web} and the modules' {@code adapter.in.web} read it ({@code ApplicationRules}
 * rule 14), and only the call wrapper binds it (rule 18). An exception handler never reads it:
 * Spring MVC runs one after the call's scope has ended. A use case takes its {@code Actor} as a
 * parameter and never reads this.
 */
public final class CurrentActor {

    private static final ScopedValue<Actor> ACTOR = ScopedValue.newInstance();

    private CurrentActor() {}

    /**
     * The actor bound around the running call.
     *
     * @return the actor
     * @throws IllegalStateException when no actor is bound: outside a web call, or on a call whose
     *     caller isn't signed in, which only an open operation lets through
     */
    public static Actor get() {
        if (!ACTOR.isBound()) {
            throw new IllegalStateException("No actor is bound: this code runs outside a signed-in web call");
        }
        return ACTOR.get();
    }

    /**
     * Runs an operation with an actor bound, and unbinds it when the operation returns or throws.
     *
     * @param actor the call's actor
     * @param operation what runs with it bound
     * @param <R> what the operation returns
     * @param <X> what the operation may throw
     * @return what the operation returned
     * @throws X what the operation threw
     */
    public static <R extends @Nullable Object, X extends Throwable> R callWith(
            Actor actor, ScopedValue.CallableOp<? extends R, X> operation) throws X {
        return ScopedValue.where(ACTOR, actor).call(operation);
    }
}
