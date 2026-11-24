package com.dispatchflow.access.application.service.account.credentiallink;

import com.dispatchflow.access.application.port.out.AdminNotice;
import com.dispatchflow.access.application.port.out.CommonPasswordList;
import com.dispatchflow.access.application.port.out.PasswordHasher;
import com.dispatchflow.access.application.port.out.PresentedPassword;
import com.dispatchflow.access.application.port.out.PresentedToken;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.PersonalData;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Objects;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link SetPasswordByLink} (ADR-129): it finds the account by the link's token
 * hash, sets the password through {@code UserAccount.setPasswordByLink}, answering the refusals
 * {@code SetPasswordByLink} names as a voided link, saves the account, reads the Admins' addresses
 * through {@code UserAccountRepository.activeAdminRecipients} when it is an Admin's password
 * (FR-389), and writes the audit record (NFR-23). The use case evicts the account and sends the
 * notice after the commit.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class SetPasswordByLinkChange {

    private final UserAccountRepository accounts;
    private final SecretTokens secretTokens;
    private final PasswordHasher passwordHasher;
    private final CommonPasswordList commonPasswordList;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param accounts where accounts are kept
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param passwordHasher hashes passwords and checks a presented one
     * @param commonPasswordList reads the bundled list of common passwords
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public SetPasswordByLinkChange(
            UserAccountRepository accounts,
            SecretTokens secretTokens,
            PasswordHasher passwordHasher,
            CommonPasswordList commonPasswordList,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.secretTokens = secretTokens;
        this.passwordHasher = passwordHasher;
        this.commonPasswordList = commonPasswordList;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Sets the password, spends the link, and saves the account.
     *
     * <p>To Be Implemented.
     *
     * @param token the token the link carried
     * @param password the new password, as typed
     * @param call the call's context: no user or session, the client and the trace (ADR-110)
     * @return what the use case evicts and sends after the commit
     * @throws RuleViolation with Access's {@code UnknownLink} when no set-password or recovery link
     *     has the token; {@code LinkAlreadyUsed}, {@code LinkVoided} or {@code LinkExpired} when
     *     the link no longer works (NFR-211, NFR-224); {@code LinkVoided} also in place of {@code
     *     AccountDeactivated} or {@code AccountAnonymized}, each audited by its own code first
     *     (NFR-145); {@code PasswordHasUnpairedSurrogate}, {@code PasswordTooShort} or {@code
     *     PasswordTooCommon} for a password the policy refuses (NFR-95)
     * @throws StaleVersion when the account changed between its read and its save (BR-76)
     */
    public PasswordSet apply(PresentedToken token, PresentedPassword password, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * What a password set through a link leaves the use case to do after the commit. The notice
     * holds the Admins' addresses, so it prints redacted (NFR-19).
     *
     * <p>To Be Implemented.
     *
     * @param account the account whose password was set, evicted from the revocation cache
     * @param adminNotice the notice every active Admin gets when it is an Admin's password;
     *     otherwise empty
     */
    public record PasswordSet(AccountId account, Optional<AdminNotice> adminNotice) implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public PasswordSet {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(adminNotice, "adminNotice");
        }

        /**
         * A form that hides the addresses.
         *
         * @return {@code SetPasswordByLinkChange.PasswordSet[redacted]}
         */
        @Override
        public String toString() {
            return "SetPasswordByLinkChange.PasswordSet[redacted]";
        }
    }
}
