package com.dispatchflow.access.application.service.account.state;

import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link DeactivateOnWrittenRequest} (ADR-129): it deactivates the Admin account
 * without the minimum of active Admins (FR-346), saves it, and writes the audit record, which holds
 * the account ID only (FR-409). The step evicts the account from the revocation cache after the
 * commit.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class DeactivateOnWrittenRequestChange {

    private final UserAccountRepository accounts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transaction.
     *
     * @param accounts where accounts are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public DeactivateOnWrittenRequestChange(UserAccountRepository accounts, Clock clock, AuditLog auditLog) {
        this.accounts = accounts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Deactivates the Admin account, and saves it.
     *
     * <p>To Be Implemented.
     *
     * @param account the Admin account the request names
     * @param call the step's server context
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active; {@code NotAnAdmin} when it holds no Admin role
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account changed between its read and its save (BR-76)
     */
    public void apply(AccountId account, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
