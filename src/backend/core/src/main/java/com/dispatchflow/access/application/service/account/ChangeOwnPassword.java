package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * A signed-in user changes their own password by giving the current one (FR-123). The new one meets
 * the policy (FR-179, NFR-95). Every session of the user ends, so they sign in again (FR-178),
 * every open link is voided (NFR-224), and an access token issued before is refused at once
 * (FR-180). The change moves the account's generation and raises {@code SessionsRevoked}, so every
 * token issued before is refused at once (NFR-90, NFR-177); the use case evicts the account from
 * the revocation cache after its commit (ADR-045). Every active Admin is emailed when it is an
 * Admin's password (FR-389): the use case reads them through {@code
 * UserAccountRepository.activeAdminRecipients} and sends {@code EmailSender.sendAdminNotice}.
 *
 * <p>A wrong current password counts toward the sign-in limits, in the rate-limit adapter (FR-177,
 * ADR-071). Access's domain has no error for a wrong password yet; M5 adds it, with {@code SignIn}.
 * Like every changing call it writes its audit record, a refused attempt's in its own transaction
 * (NFR-23, ADR-110).
 *
 * <p><b>The transaction.</b> {@link ChangeOwnPasswordChange} holds it: the password check, the
 * change, its save, the Admins' addresses and the audit record. After its commit, the use case
 * evicts the account from the revocation cache and sends the Admins' notice, so a change that rolls
 * back sends nothing (ADR-129).
 *
 * <p>Built in M6.
 */
public final class ChangeOwnPassword {

    private final RevocationCache revocationCache;
    private final EmailSender emailSender;
    private final ChangeOwnPasswordChange change;

    /**
     * Creates the use case.
     *
     * @param revocationCache the token check's cache, evicted after a change
     * @param emailSender sends links and notices by email
     * @param change the transaction that changes the password
     */
    public ChangeOwnPassword(RevocationCache revocationCache, EmailSender emailSender, ChangeOwnPasswordChange change) {
        this.revocationCache = revocationCache;
        this.emailSender = emailSender;
        this.change = change;
    }

    /**
     * Changes the password.
     *
     * <p>Built in M6.
     *
     * @param actor the signed-in user acting
     * @param current the current password, as typed
     * @param next the new password, as typed
     * @throws RuleViolation with {@code NewPassword}'s {@code PasswordHasUnpairedSurrogate}, {@code
     *     PasswordTooShort} or {@code PasswordTooCommon} for a new password the policy refuses
     *     (NFR-95); {@code AccountDeactivated} or {@code AccountAnonymized} when the account is not
     *     active; the wrong-password refusal M5 adds
     * @throws NotFound when the actor's account no longer exists
     * @throws StaleVersion when the account changed between its read and its save (BR-76); the web
     *     adapter retries the call at once, up to three times, then answers 503 with {@code
     *     Retry-After} (ADR-121)
     */
    public void change(Actor actor, PresentedPassword current, PresentedPassword next) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
