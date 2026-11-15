package com.dispatchflow.access.application.api;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The signed-in user a web call acts for, which the web adapter passes first to every web-called
 * use case (DL §2.1). The handler was already authorized by role from its operation's {@code
 * x-capability}; a check that depends on the data, such as a driver's own route, is the use
 * case's (ADR-068).
 *
 * <p>M5 builds sign-in. Until then the web adapter builds the actor from a stand-in session of a
 * seeded account (ADR-121). The stand-in fails closed: it exists only when {@code dev} or {@code
 * test} is the only active profile, and the core refuses to boot with the stand-in and any other
 * profile active, which a test proves. M5 deletes the stand-in. So the calls that M0 to M4 build,
 * which take an actor, run before sign-in exists.
 *
 * <p>Built in M0.
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
