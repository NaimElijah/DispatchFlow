package com.dispatchflow.access.application.service.account.credentiallink;

import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.application.port.out.PresentedToken;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Sets a password through a set-password or recovery link, which spends it (FR-294, FR-345,
 * NFR-211). The new password meets the policy (NFR-95). Like any password change, it voids every
 * other open link (NFR-224), and ends every session of the user (FR-345). The change moves the
 * account's generation and raises {@code SessionsRevoked}, so every token issued before is refused
 * at once (NFR-90, NFR-177); the use case evicts the account from the revocation cache after its
 * commit (ADR-045). Every active Admin is emailed when it is an Admin's password (FR-389): the use
 * case reads them through {@code UserAccountRepository.activeAdminRecipients} and sends {@code
 * EmailSender.sendAdminNotice}.
 *
 * <p>A token no account's link holds is refused as {@code UnknownLink}, the same as a token the
 * account holds no such link for, so the page learns nothing more. Like every changing call it
 * writes its audit record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p><b>Refusals answered as a voided link</b> (NFR-145, NFR-224), as {@code ConfirmEmailByLink}
 * answers them. The holder of the link must learn nothing about the account's state, so the use
 * case audits {@code UserAccount.setPasswordByLink}'s {@code AccountDeactivated} and {@code
 * AccountAnonymized} as refused attempts with the domain's specific code, once the transaction they
 * refused has rolled back (ADR-110, ADR-129 decision 5), and throws a {@code RuleViolation} with
 * Access's {@code LinkVoided} in their place. A deactivation voids every unspent link (NFR-224), so for an unspent link {@code
 * LinkVoided} is what happened to it.
 *
 * <p><b>Gap until the email links are built.</b> {@code UserAccount} checks the account's state
 * before the link's, so a spent link answers {@code LinkAlreadyUsed} on an active account and
 * {@code LinkVoided} on a deactivated one. The email links check the link's state first, so a spent
 * link answers alike in every state (NFR-145).
 *
 * <p><b>The transaction.</b> {@link SetPasswordByLinkChange} holds it: the link's lookup, the
 * password with the refusals above, its save, the Admins' addresses and the audit record. After its
 * commit, the use case evicts the account from the revocation cache and sends the Admins' notice,
 * so a change that rolls back sends nothing (ADR-129).
 *
 * <p>To Be Implemented.
 */
public final class SetPasswordByLink {

    private final RevocationCache revocationCache;
    private final EmailSender emailSender;
    private final SetPasswordByLinkChange change;

    /**
     * Creates the use case.
     *
     * @param revocationCache the token check's cache, evicted after a change
     * @param emailSender sends links and notices by email
     * @param change the transaction that sets the password
     */
    public SetPasswordByLink(RevocationCache revocationCache, EmailSender emailSender, SetPasswordByLinkChange change) {
        this.revocationCache = revocationCache;
        this.emailSender = emailSender;
        this.change = change;
    }

    /**
     * Sets the password and spends the link.
     *
     * <p>To Be Implemented.
     *
     * @param token the token the link carried
     * @param password the new password, as typed
     * @param call the call's context: no user or session, the client and the trace (ADR-110)
     * @throws RuleViolation with Access's {@code UnknownLink} when no set-password or recovery link
     *     has the token; {@code LinkAlreadyUsed}, {@code LinkVoided} or {@code LinkExpired} when
     *     the link no longer works (NFR-211, NFR-224); {@code LinkVoided} also in place of {@code
     *     AccountDeactivated} or {@code AccountAnonymized}, when the account is not active, as
     *     above (NFR-145); {@code PasswordHasUnpairedSurrogate}, {@code PasswordTooShort} or {@code
     *     PasswordTooCommon} for a password the policy refuses (NFR-95)
     * @throws StaleVersion when the account changed between its read and its save (BR-76); the web
     *     adapter retries the call at once, up to three times, then answers 503 with {@code
     *     Retry-After} (ADR-121)
     */
    public void set(PresentedToken token, PresentedPassword password, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
