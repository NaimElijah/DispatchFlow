package com.dispatchflow.access.application.service.account;

import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.RecoveryEmailCap;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.IdGenerator;

/**
 * A user who forgot their password asks for a recovery link at their address (FR-386). The link
 * works once and for 1 hour (FR-345), and asking for it leaves the current password working. No
 * answer waits for the email to be sent (NFR-225).
 *
 * <p><b>Refusals answered alike</b> (NFR-145): an address no account holds, which returns normally
 * and sends nothing; the refusals of {@code UserAccount.issueLink} for a recovery link: {@code
 * AccountDeactivated}, {@code AccountAnonymized} and {@code AccountHasNoRole} (BR-123); FR-172's
 * limit of 5 recoveries an hour per account; NFR-215's cap of 100 recovery emails a day; and a
 * version conflict on the save, where a concurrent request for the same account won and is issuing
 * the link, so no {@code StaleVersion} leaves the use case. The recovery endpoint answers every one
 * as "sent", after the same work, so no caller learns whether the address has an account or why
 * nothing was sent.
 *
 * <p><b>The two limits.</b> FR-172's limit is the web adapter's Bucket4j limit, keyed by the
 * canonical address (ADR-071), so a request over it never reaches this use case. NFR-215's cap is
 * counted on the send path, through {@link RecoveryEmailCap}: the use case takes from it only when
 * it is about to send a link, on the Israel day of the clock's instant (ADR-088), and sends nothing
 * when the take is refused. So an address no account holds, or a refused one, never uses up the
 * cap. The residual risk: about 20 real addresses, each at FR-172's 5 an hour, can still spend the
 * day's cap within an hour; ADR-036 revisits the limits on a day the cap is reached.
 *
 * <p><b>The order.</b> The use case calls {@code UserAccount.issueLink} first, then {@link
 * RecoveryEmailCap#take}, and saves the account and sends the link only when the take succeeds. On a
 * refused take the account isn't saved, so the link issued in memory is dropped and the account's
 * earlier open link, if it has one, stays valid. The take is in the same transaction as the save, so
 * a version conflict on the save rolls the take back and a lost race spends none of the cap.
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M6.
 */
public final class RequestPasswordRecovery {

    private final UserAccountRepository accounts;
    private final SecretTokens secretTokens;
    private final EmailSender emailSender;
    private final RecoveryEmailCap recoveryEmailCap;
    private final IdGenerator ids;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param accounts where accounts are kept
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param emailSender sends links and notices by email
     * @param recoveryEmailCap NFR-215's daily cap of recovery emails, taken from before each send
     * @param ids makes new IDs
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public RequestPasswordRecovery(
            UserAccountRepository accounts,
            SecretTokens secretTokens,
            EmailSender emailSender,
            RecoveryEmailCap recoveryEmailCap,
            IdGenerator ids,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.secretTokens = secretTokens;
        this.emailSender = emailSender;
        this.recoveryEmailCap = recoveryEmailCap;
        this.ids = ids;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Sends the recovery link, when one may be sent and the day's cap has room. The caller answers
     * "sent" whether or not a link went out.
     *
     * <p>Built in M6.
     *
     * @param email the address typed
     * @param call the call's context: no user or session, the client and the trace (ADR-110)
     * @throws RuleViolation with a refusal of {@code UserAccount.issueLink} named above, which the
     *     endpoint answers as "sent"
     */
    public void request(EmailAddress email, CallContext call) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
