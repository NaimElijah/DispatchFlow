package com.dispatchflow.access.application.port.out.session;

import com.dispatchflow.access.domain.AccountGeneration;
import com.dispatchflow.access.domain.account.RoleSet;
import com.dispatchflow.access.domain.session.SessionGeneration;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import java.time.Instant;
import java.util.Objects;

/**
 * What an access token says (ADR-045): whose it is, which session issued it, the two generations
 * the token check compares, and the roles it may use (NFR-86, NFR-177, NFR-227). It holds no
 * personal data.
 *
 * <p>Built in M5.
 *
 * @param account the account, the token's {@code sub}
 * @param accountGeneration the account's generation, the token's {@code ugen}
 * @param session the session, the token's {@code sid}
 * @param sessionGeneration the session's generation, the token's {@code sgen}
 * @param roles the roles the account holds now
 * @param issuedAt when the token is issued
 * @param expiresAt when it stops being accepted, 15 minutes later by default (NFR-86); until M6
 *     builds refresh, the {@code dev} profile alone may set a longer lifetime (ADR-121), and
 *     unless {@code dev} is the only active profile the core refuses to boot with a lifetime other
 *     than 15 minutes, which M5 tests. M6 deletes the dev allowance
 */
public record AccessClaims(
        AccountId account,
        AccountGeneration accountGeneration,
        SessionId session,
        SessionGeneration sessionGeneration,
        RoleSet roles,
        Instant issuedAt,
        Instant expiresAt) {

    /** Checks every part is present. */
    public AccessClaims {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(accountGeneration, "accountGeneration");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(sessionGeneration, "sessionGeneration");
        Objects.requireNonNull(roles, "roles");
        Objects.requireNonNull(issuedAt, "issuedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }
}
