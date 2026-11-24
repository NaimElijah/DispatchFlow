package com.dispatchflow.access.application.service.account.role;

import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transaction of {@link RemoveDriverRoleService} (ADR-129): it takes the Driver role off the
 * account through {@code UserAccount.removeDriverRole}, saves it, and writes the audit record with
 * the listener's server step (NFR-23, ADR-110). Once the revocation cache is built, the service
 * evicts the account from it after the commit.
 *
 * <p>To Be Implemented.
 */
@Transactional
public class RemoveDriverRoleServiceChange {

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
    public RemoveDriverRoleServiceChange(UserAccountRepository accounts, Clock clock, AuditLog auditLog) {
        this.accounts = accounts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Takes the Driver role off the account, and saves it.
     *
     * <p>To Be Implemented.
     *
     * @param driver the retired driver's account
     * @param call the listener's server step
     * @throws NotFound when no account has the ID
     * @throws StaleVersion when the account changed between its read and its save (BR-76)
     */
    public void apply(AccountId driver, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
