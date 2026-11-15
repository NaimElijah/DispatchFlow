package com.dispatchflow.access.application.api;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * The signed-in user a web call acts for, which the web adapter passes first to every web-called
 * use case (DL §2.1). The handler was already authorized by role from its operation's {@code
 * x-capability}; a check that depends on the data, such as a driver's own route, is the use
 * case's (ADR-068).
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
        // EnumSet.copyOf refuses an empty collection that is not itself an EnumSet.
        roles = roles.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(ActorRole.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }
}
