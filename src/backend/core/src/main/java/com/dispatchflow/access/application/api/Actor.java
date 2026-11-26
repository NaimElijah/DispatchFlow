package com.dispatchflow.access.application.api;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The signed-in user a web call acts for, which a module's controller passes first to every
 * web-called use case (DL §2.1). The controller gets it from {@code CurrentActor}, which {@code
 * web} binds around the call (ADR-140). The handler was already authorized by role from its
 * operation's {@code x-capability}; a check that depends on the data, such as a driver's own
 * route, is the use case's (ADR-068).
 *
 * <p>Only {@code web.security} builds one ({@code ApplicationRules} rule 17), from the request's
 * principal and the chosen handler, and a test builds one through {@code TestActors}. Sign-in is
 * not built yet. Until it is, the principal is a stand-in session of a seeded account (ADR-121,
 * ADR-140, ADR-147). The stand-in fails closed: it exists only when the active profiles are exactly
 * {@code dev} or exactly {@code test}, and the core refuses to boot with its configuration under
 * any other profiles or in a deployed environment, which {@code StandInGuardTest} proves. Building
 * sign-in deletes the stand-in. So the calls built before sign-in, which take an actor, run before
 * sign-in exists.
 *
 * @param account the acting user's account
 * @param roles the roles the account held when its access token was issued
 * @param call the call's audit context, which the use case writes its audit record with
 */
public record Actor(AccountId account, Set<ActorRole> roles, CallContext call) {

    /** Checks every part is present, and copies the roles. */
    public Actor {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(call, "call");
        // The audit record names the user and session from the call context (NFR-93), so they
        // must be the acting account's own.
        if (!call.user().equals(Optional.of(account)) || call.session().isEmpty()) {
            throw new IllegalArgumentException("the call context names another user or no session");
        }
        // EnumSet.copyOf refuses an empty collection that is not itself an EnumSet.
        roles = roles.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(ActorRole.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }
}
