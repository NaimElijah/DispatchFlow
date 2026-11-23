package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.RemoveDriverRole;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Access's implementation of {@code RemoveDriverRole}: it takes the Driver role off a retired
 * driver's account through {@code UserAccount.removeDriverRole}, keeping its other roles, in any
 * state (FR-307, ADR-104). The change moves the account's generation and raises {@code
 * SessionsRevoked}, so every token issued before is refused at once (NFR-90, NFR-177); the use case
 * evicts the account from the revocation cache after its commit (ADR-045). It writes its audit
 * record with the listener's server step (NFR-23, ADR-110).
 *
 * <p>In M2 it changes the account and writes the audit record. The eviction from {@link
 * RevocationCache} joins in M6, which builds the cache; until then the token check has no cache to
 * evict.
 *
 * <p><b>The transaction.</b> {@link RemoveDriverRoleServiceChange} holds it: the change, its save
 * and the audit record. The eviction runs after its commit (ADR-129).
 *
 * <p>Built in M2.
 */
public final class RemoveDriverRoleService implements RemoveDriverRole {

    private final RevocationCache revocationCache;
    private final RemoveDriverRoleServiceChange change;

    /**
     * Creates the service.
     *
     * @param revocationCache the token check's cache, evicted after a change
     * @param change the transaction that removes the role
     */
    public RemoveDriverRoleService(RevocationCache revocationCache, RemoveDriverRoleServiceChange change) {
        this.revocationCache = revocationCache;
        this.change = change;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M2.
     *
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account changed between its read and its save (BR-76); Fleet's
     *     listener adapter retries at once, up to three times (DL §11)
     */
    @Override
    public void remove(AccountId driver, CallContext call) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
