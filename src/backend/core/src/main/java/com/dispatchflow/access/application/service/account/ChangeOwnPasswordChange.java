package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.AdminNotice;
import com.dispatchflow.access.application.port.out.CommonPasswordList;
import com.dispatchflow.access.application.port.out.PasswordHasher;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link ChangeOwnPassword} (ADR-129): it checks the current password, changes
 * the password through the account, saves it, reads the Admins' addresses through {@code
 * UserAccountRepository.activeAdminRecipients} when it is an Admin's password (FR-389), and writes
 * the audit record (NFR-23). The use case evicts the account and sends the notice after the commit.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class ChangeOwnPasswordChange {

    private final UserAccountRepository accounts;
    private final PasswordHasher passwordHasher;
    private final CommonPasswordList commonPasswordList;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param accounts where accounts are kept
     * @param passwordHasher hashes passwords and checks a presented one
     * @param commonPasswordList reads the bundled list of common passwords
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public ChangeOwnPasswordChange(
            UserAccountRepository accounts,
            PasswordHasher passwordHasher,
            CommonPasswordList commonPasswordList,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.passwordHasher = passwordHasher;
        this.commonPasswordList = commonPasswordList;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Changes the password, and saves the account.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @param current the current password, as typed
     * @param next the new password, as typed
     * @return the notice every active Admin gets when it is an Admin's password; otherwise empty
     * @throws RuleViolation with {@code NewPassword}'s {@code PasswordHasUnpairedSurrogate}, {@code
     *     PasswordTooShort} or {@code PasswordTooCommon} for a new password the policy refuses
     *     (NFR-95); {@code AccountDeactivated} or {@code AccountAnonymized} when the account is not
     *     active; the wrong-password refusal {@code SignIn} adds
     * @throws NotFound when the actor's account no longer exists
     * @throws StaleVersion when the account changed between its read and its save (BR-76)
     */
    public Optional<AdminNotice> apply(Actor actor, PresentedPassword current, PresentedPassword next) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
