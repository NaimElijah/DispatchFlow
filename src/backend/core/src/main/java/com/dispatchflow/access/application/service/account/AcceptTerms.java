package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;
import org.springframework.transaction.annotation.Transactional;

/**
 * The signed-in user accepts the terms and the privacy notice, which is recorded (FR-77). The first
 * acceptance is kept, and the apps never ask for it during a route. Like every changing call it
 * writes its audit record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>To Be Implemented.
 */
@Transactional
public class AcceptTerms {

    private final UserAccountRepository accounts;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param accounts where accounts are kept
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public AcceptTerms(UserAccountRepository accounts, Clock clock, AuditLog auditLog) {
        this.accounts = accounts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Records the acceptance.
     *
     * <p>To Be Implemented.
     *
     * @param actor the signed-in user acting
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active
     * @throws NotFound when the actor's account no longer exists
     * @throws StaleVersion when the account changed between its read and its save (BR-76); the web
     *     adapter retries the call at once, up to three times, then answers 503 with {@code
     *     Retry-After} (ADR-121)
     */
    public void accept(Actor actor) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
