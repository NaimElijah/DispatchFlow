package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.Version;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * An Admin ends every session of a user without deactivating them (FR-187), which also voids every
 * open link (NFR-224). An access token issued before is refused at once (FR-188). The change moves
 * the account's generation and raises {@code SessionsRevoked}, so every token issued before is
 * refused at once (NFR-90, NFR-177); the use case evicts the account from the revocation cache
 * after its commit (ADR-045).
 *
 * <p>The operation is Admin-only in the permission matrix, so the acting session must have given
 * its password in the last 5 minutes, which {@code Session.requireFreshStepUp} checks (NFR-214,
 * NFR-227). Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p><b>The transaction.</b> {@link EndEverySessionChange} holds it: the step-up check, the change,
 * its save and the audit record. After its commit, the use case evicts the account from the
 * revocation cache (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class EndEverySession {

    private final RevocationCache revocationCache;
    private final EndEverySessionChange change;

    /**
     * Creates the use case.
     *
     * @param revocationCache the token check's cache, evicted after a change
     * @param change the transaction that ends the sessions
     */
    public EndEverySession(RevocationCache revocationCache, EndEverySessionChange change) {
        this.revocationCache = revocationCache;
        this.change = change;
    }

    /**
     * Ends the sessions.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @param account the user's account
     * @param expected the version of the account the caller last read (BR-76)
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active; {@code StepUpRequired} when the acting session gave its password
     *     more than 5 minutes ago, or {@code SessionRevoked} when it is revoked (NFR-214)
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account is no longer at {@code expected}, or changed before the
     *     save (BR-76)
     */
    public void end(Actor actor, AccountId account, Version expected) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
