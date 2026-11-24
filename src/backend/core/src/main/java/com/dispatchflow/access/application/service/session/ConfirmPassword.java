package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * The acting session gives the current password before an Admin-only operation, which allows those
 * operations for 5 minutes (NFR-214). {@code Session.confirmPassword} moves the session's
 * generation, so its older tokens are refused, and it gets new ones (NFR-227). The use case evicts
 * the session from the revocation cache after its commit (ADR-045).
 *
 * <p>A wrong password counts toward the sign-in limits, in the rate-limit adapter (FR-177,
 * ADR-071). Access's domain has no error for a wrong password yet; it comes with {@code SignIn}.
 * Like every changing call it writes its audit record, a refused attempt's in its own transaction
 * (NFR-23, ADR-110).
 *
 * <p><b>The transaction.</b> {@link ConfirmPasswordChange} holds it: the password check, the
 * confirmation, the new tokens, the save and the audit record. After its commit, the use case
 * evicts the session from the revocation cache (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class ConfirmPassword {

    private final RevocationCache revocationCache;
    private final ConfirmPasswordChange change;

    /**
     * Creates the use case.
     *
     * @param revocationCache the token check's cache, evicted after a change
     * @param change the transaction that confirms the password
     */
    public ConfirmPassword(RevocationCache revocationCache, ConfirmPasswordChange change) {
        this.revocationCache = revocationCache;
        this.change = change;
    }

    /**
     * Confirms the password.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @param password the current password, as typed
     * @return the session's new tokens
     * @throws RuleViolation with Access's {@code SessionRevoked} when the session is revoked, or
     *     its account's sessions were revoked after it began (NFR-177); or with the wrong-password
     *     refusal {@code SignIn} adds
     * @throws NotFound when the actor's session or account no longer exists
     * @throws StaleVersion when the session changed between its read and its save (BR-76); the web
     *     adapter retries the call at once, up to three times, then answers 503 with {@code
     *     Retry-After} (ADR-121)
     */
    public SignedIn confirm(Actor actor, PresentedPassword password) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
