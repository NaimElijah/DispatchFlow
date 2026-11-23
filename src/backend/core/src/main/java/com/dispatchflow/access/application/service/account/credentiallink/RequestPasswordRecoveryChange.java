package com.dispatchflow.access.application.service.account.credentiallink;

import com.dispatchflow.access.application.port.out.RecoveryEmailCap;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.application.service.account.LinkEmail;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RequestPasswordRecovery} (ADR-129): it finds the account by the address,
 * issues the recovery link through {@code UserAccount.issueLink}, takes from {@link
 * RecoveryEmailCap}, saves the account only when the take succeeds, and writes the audit record
 * (NFR-23). Every refusal {@code RequestPasswordRecovery} answers alike is audited here, and none
 * leaves as a {@code RuleViolation}. The use case sends the link after the commit.
 *
 * <p>Built in M6.
 */
@Transactional
public class RequestPasswordRecoveryChange {

    private final UserAccountRepository accounts;
    private final SecretTokens secretTokens;
    private final RecoveryEmailCap recoveryEmailCap;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param accounts where accounts are kept
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param recoveryEmailCap NFR-215's daily cap of recovery emails, taken from before each send
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public RequestPasswordRecoveryChange(
            UserAccountRepository accounts,
            SecretTokens secretTokens,
            RecoveryEmailCap recoveryEmailCap,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.secretTokens = secretTokens;
        this.recoveryEmailCap = recoveryEmailCap;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Issues the recovery link and saves the account, when one may be sent and the day's cap has
     * room.
     *
     * <p>Built in M6.
     *
     * @param email the address typed
     * @param call the call's context: no user or session, the client and the trace (ADR-110)
     * @return the link's email, to the address stored on the account (BR-125); empty when nothing is
     *     to be sent
     * @throws StaleVersion when a concurrent request for the same account won the save (BR-76); the
     *     use case answers it as a request that sent nothing
     */
    public Optional<LinkEmail> apply(EmailAddress email, CallContext call) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
