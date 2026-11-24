package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.session.TokenSigner;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * The server step a restore runs before the core serves again (ADR-045): it rotates the
 * access-token signing key and removes the old key from the accepted set at once, with no overlap
 * (NFR-272), revokes every session (NFR-252), and voids every open credential link (NFR-275). A
 * restored database holds its snapshot's generations and links, so without this a token issued or
 * revoked after the snapshot could read as valid, and a link sent before the snapshot could still
 * be used.
 *
 * <p><b>The order.</b> It rotates the key through {@code TokenSigner.rotateKey} first: it is one
 * step, and it refuses every access token signed before the restore, whereas the revocations are
 * one transaction each. So a step that stops part way has already closed the widest gap, and
 * running it again finishes the rest. Then it revokes each session {@code
 * SessionRepository.allLive} names, and voids the links of each account {@code
 * UserAccountRepository.withOpenLinks} names, each in its own transaction.
 *
 * <p><b>The transactions.</b> The key's rotation runs before any transaction. {@link
 * SecureAfterRestoreChange} lists the live sessions and the accounts with open links, each in a
 * read-only transaction, and holds each session's revocation and each account's voiding, one
 * transaction each (ADR-129). After each revocation commits, the step evicts the session from the
 * revocation cache (ADR-045).
 *
 * <p>The restore runbook's other steps are not declared yet: re-applying the changes made since the
 * backup (NFR-253), blanking the passwords and voiding the links of the accounts whose password or
 * email changed since it (NFR-274), and issuing the Admins their set-password links (NFR-282). They
 * follow ADR-045.
 *
 * <p>Domain gap: {@code UserAccount} has no method that voids every open link yet.
 *
 * <p>To Be Implemented.
 */
public final class SecureAfterRestore {

    private final TokenSigner tokenSigner;
    private final RevocationCache revocationCache;
    private final SecureAfterRestoreChange change;

    /**
     * Creates the step.
     *
     * @param tokenSigner signs access tokens
     * @param revocationCache the token check's cache, evicted after a change
     * @param change the transactions that list the sessions and accounts, revoke one session and
     *     void one account's links
     */
    public SecureAfterRestore(
            TokenSigner tokenSigner, RevocationCache revocationCache, SecureAfterRestoreChange change) {
        this.tokenSigner = tokenSigner;
        this.revocationCache = revocationCache;
        this.change = change;
    }

    /**
     * Rotates the signing key, then revokes every session and voids every open credential link.
     *
     * <p>To Be Implemented.
     *
     * @param call the step's server context
     * @throws StaleVersion when a session or an account changed between its read and its save
     *     (BR-76)
     */
    public void secure(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
