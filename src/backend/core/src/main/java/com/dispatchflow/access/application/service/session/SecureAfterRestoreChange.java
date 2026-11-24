package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.port.out.SessionRepository;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.application.port.out.session.PushTokens;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactions of {@link SecureAfterRestore} (ADR-129): {@link #liveSessions} and {@link
 * #accountsWithOpenLinks} list the items, read-only; {@link #revoke} revokes one session, removes
 * its push address through {@code PushTokens.removeFor} (NFR-263) and writes its audit record;
 * {@link #voidLinks} voids one account's open credential links (NFR-275) and writes its audit
 * record (NFR-23).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class SecureAfterRestoreChange {

    private final SessionRepository sessions;
    private final UserAccountRepository accounts;
    private final PushTokens pushTokens;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the transactions.
     *
     * @param sessions where sessions are kept
     * @param accounts where accounts are kept, with their credential links
     * @param pushTokens the phones' push addresses, each revoked session's removed (NFR-263)
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public SecureAfterRestoreChange(
            SessionRepository sessions,
            UserAccountRepository accounts,
            PushTokens pushTokens,
            Clock clock,
            AuditLog auditLog) {
        this.sessions = sessions;
        this.accounts = accounts;
        this.pushTokens = pushTokens;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * The live sessions, through {@code SessionRepository.allLive}.
     *
     * <p>To Be Implemented.
     *
     * @return their IDs, in any order
     */
    @Transactional(readOnly = true)
    public List<SessionId> liveSessions() {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * The accounts that hold an open credential link, through {@code
     * UserAccountRepository.withOpenLinks}.
     *
     * <p>To Be Implemented.
     *
     * @return their IDs, in any order
     */
    @Transactional(readOnly = true)
    public List<AccountId> accountsWithOpenLinks() {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Revokes one session, and stores it.
     *
     * <p>To Be Implemented.
     *
     * @param session a session {@link #liveSessions} listed
     * @param call the step's server context
     * @throws StaleVersion when the session changed between its read and its save (BR-76)
     */
    public void revoke(SessionId session, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }

    /**
     * Voids one account's open credential links, and saves it.
     *
     * <p>To Be Implemented.
     *
     * @param account an account {@link #accountsWithOpenLinks} listed
     * @param call the step's server context
     * @throws StaleVersion when the account changed between its read and its save (BR-76)
     */
    public void voidLinks(AccountId account, CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
