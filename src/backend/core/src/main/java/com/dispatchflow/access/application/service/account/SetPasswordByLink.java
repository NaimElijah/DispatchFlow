package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.port.out.CommonPasswordList;
import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.PasswordHasher;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.application.port.out.PresentedToken;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
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
 * case catches {@code UserAccount.setPasswordByLink}'s {@code AccountDeactivated} and {@code
 * AccountAnonymized}, writes {@code AuditLog.refused} with the domain's specific code in its own
 * transaction (ADR-110), and then throws a {@code RuleViolation} with Access's {@code LinkVoided}
 * in its place. A deactivation voids every unspent link (NFR-224), so {@code LinkVoided} is what
 * happened to the link.
 *
 * <p>Built in M6.
 */
public final class SetPasswordByLink {

    private final UserAccountRepository accounts;
    private final SecretTokens secretTokens;
    private final PasswordHasher passwordHasher;
    private final CommonPasswordList commonPasswordList;
    private final RevocationCache revocationCache;
    private final EmailSender emailSender;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param accounts where accounts are kept
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param passwordHasher hashes passwords and checks a presented one
     * @param commonPasswordList reads the bundled list of common passwords
     * @param revocationCache the token check's cache, evicted after a change
     * @param emailSender sends links and notices by email
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public SetPasswordByLink(
            UserAccountRepository accounts,
            SecretTokens secretTokens,
            PasswordHasher passwordHasher,
            CommonPasswordList commonPasswordList,
            RevocationCache revocationCache,
            EmailSender emailSender,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.secretTokens = secretTokens;
        this.passwordHasher = passwordHasher;
        this.commonPasswordList = commonPasswordList;
        this.revocationCache = revocationCache;
        this.emailSender = emailSender;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Sets the password and spends the link.
     *
     * <p>Built in M6.
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
        throw new UnsupportedOperationException("Built in M6");
    }
}
