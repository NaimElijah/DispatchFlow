package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.CommonPasswordList;
import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.PasswordHasher;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
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
 * <p>Built in M6.
 */
public final class ChangeOwnPassword {

    private final UserAccountRepository accounts;
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
     * @param passwordHasher hashes passwords and checks a presented one
     * @param commonPasswordList reads the bundled list of common passwords
     * @param revocationCache the token check's cache, evicted after a change
     * @param emailSender sends links and notices by email
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public ChangeOwnPassword(
            UserAccountRepository accounts,
            PasswordHasher passwordHasher,
            CommonPasswordList commonPasswordList,
            RevocationCache revocationCache,
            EmailSender emailSender,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.commonPasswordList = commonPasswordList;
        this.revocationCache = revocationCache;
        this.emailSender = emailSender;
        this.clock = clock;
        this.auditLog = auditLog;
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
     * @throws StaleVersion when the account changed between its read and its save (BR-76)
     */
    public void change(Actor actor, PresentedPassword current, PresentedPassword next) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
