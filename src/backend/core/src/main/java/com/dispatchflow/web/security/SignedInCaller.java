package com.dispatchflow.web.security;

import com.dispatchflow.access.application.api.ActorRole;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The request's one principal: the account, the session and the roles of a caller who is signed in
 * (ADR-140 decision 4). The stand-in session sets it now, and the token filter from the access
 * token after sign-in (#199). A request that has none is not signed in. It is kept in one request
 * attribute, which only this package reads and writes.
 *
 * @param account the caller's account
 * @param session the caller's session
 * @param roles the roles the account held when its session or token was issued
 */
record SignedInCaller(AccountId account, SessionId session, Set<ActorRole> roles) {

    private static final String ATTRIBUTE = SignedInCaller.class.getName();

    /** Checks every part is present, and copies the roles. */
    SignedInCaller {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(roles, "roles");
        // EnumSet.copyOf refuses an empty collection that is not itself an EnumSet.
        roles = roles.isEmpty()
                ? Collections.unmodifiableSet(EnumSet.noneOf(ActorRole.class))
                : Collections.unmodifiableSet(EnumSet.copyOf(roles));
    }

    /**
     * Makes this the request's principal.
     *
     * @param request the request
     */
    void signIn(HttpServletRequest request) {
        request.setAttribute(ATTRIBUTE, this);
    }

    /**
     * The request's principal.
     *
     * @param request the request
     * @return the principal, or empty when the caller isn't signed in
     */
    static Optional<SignedInCaller> of(HttpServletRequest request) {
        return request.getAttribute(ATTRIBUTE) instanceof SignedInCaller caller
                ? Optional.of(caller)
                : Optional.empty();
    }
}
