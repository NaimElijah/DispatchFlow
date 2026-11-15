package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.api.Actor;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.Language;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * The signed-in user chooses their interface language, Hebrew or English, which follows them to
 * every device (FR-124, FR-181, NFR-27). It revokes nothing. Like every changing call it writes its
 * audit record, a refused attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M6.
 */
public final class ChangeLanguage {

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
    public ChangeLanguage(UserAccountRepository accounts, Clock clock, AuditLog auditLog) {
        this.accounts = accounts;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Changes the language.
     *
     * <p>Built in M6.
     *
     * @param actor the signed-in user acting
     * @param language the language
     * @throws RuleViolation with {@code AccountDeactivated} or {@code AccountAnonymized} when the
     *     account is not active
     * @throws NotFound when the actor's account no longer exists
     * @throws StaleVersion when the account changed between its read and its save (BR-76); the web
     *     adapter retries the call at once, up to three times, then answers 503 with {@code
     *     Retry-After} (ADR-121)
     */
    public void change(Actor actor, Language language) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
