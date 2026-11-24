package com.dispatchflow.access.application.service.account.credentiallink;

import com.dispatchflow.access.application.port.out.AdminNotice;
import com.dispatchflow.access.application.port.out.PresentedToken;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.account.EmailAddress;
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
 * The transaction of {@link ConfirmEmailByLink} (ADR-129): it finds the account by the link's token
 * hash, confirms the new address through {@code UserAccount.confirmEmailByLink}, answering the
 * refusals {@code ConfirmEmailByLink} names as a voided link, saves the account, reads the Admins'
 * addresses through {@code UserAccountRepository.activeAdminRecipients} when it is an Admin's new
 * email (FR-389), and writes the audit record (NFR-23). The use case evicts the account and sends
 * the emails after the commit.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class ConfirmEmailByLinkChange {

    private final UserAccountRepository accounts;
    private final SecretTokens secretTokens;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param accounts where accounts are kept
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public ConfirmEmailByLinkChange(
            UserAccountRepository accounts, SecretTokens secretTokens, Clock clock, AuditLog auditLog) {
        this.accounts = accounts;
        this.secretTokens = secretTokens;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Confirms the new address, spends the link, and saves the account.
     *
     * <p>To Be Implemented.
     *
     * @param token the token the link carried
     * @param call the call's context: no user or session, the client and the trace (ADR-110)
     * @return what the use case sends and evicts after the commit
     * @throws RuleViolation with Access's {@code UnknownLink} when no email-change link has the
     *     token; {@code LinkAlreadyUsed} or {@code LinkExpired} when the link no longer works;
     *     {@code LinkVoided} when it was voided, and in place of {@code EmailOfAnotherAdmin}, {@code
     *     AccountDeactivated} or {@code AccountAnonymized}, each audited by its own code first;
     *     {@code EmailInUse} when another account holds the address (FR-392)
     * @throws StaleVersion when the account changed between its read and its save (BR-76)
     */
    public Confirmed apply(PresentedToken token, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * What a confirmed email leaves the use case to do after the commit. It holds addresses, so it
     * prints redacted (NFR-19).
     *
     * <p>To Be Implemented.
     *
     * @param account the account whose email changed, evicted from the revocation cache
     * @param oldAddress the address the account no longer signs in with, which is told (FR-388)
     * @param language the language the account's user reads
     * @param adminNotice the notice every active Admin gets when it is an Admin's new email;
     *     otherwise empty
     */
    public record Confirmed(
            AccountId account, EmailAddress oldAddress, Language language, Optional<AdminNotice> adminNotice)
            implements PersonalData {

        /** Checks every part is present, possibly empty. */
        public Confirmed {
            Objects.requireNonNull(account, "account");
            Objects.requireNonNull(oldAddress, "oldAddress");
            Objects.requireNonNull(language, "language");
            Objects.requireNonNull(adminNotice, "adminNotice");
        }

        /**
         * A form that hides the addresses.
         *
         * @return {@code ConfirmEmailByLinkChange.Confirmed[redacted]}
         */
        @Override
        public String toString() {
            return "ConfirmEmailByLinkChange.Confirmed[redacted]";
        }
    }
}
