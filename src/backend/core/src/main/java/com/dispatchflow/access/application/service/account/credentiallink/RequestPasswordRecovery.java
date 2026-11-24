package com.dispatchflow.access.application.service.account.credentiallink;

import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.RecoveryEmailCap;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.audit.application.api.CallContext;

/**
 * A user who forgot their password asks for a recovery link at their address (FR-386). The link
 * works once and for 1 hour (FR-345), and asking for it leaves the current password working. No
 * answer waits for the email to be sent (NFR-225).
 *
 * <p><b>Refusals answered alike</b> (NFR-145). The use case returns normally, and sends nothing,
 * for each of these, so the recovery endpoint answers every request as "sent" and no caller learns
 * whether the address has an account or why nothing was sent:
 *
 * <ul>
 *   <li>an address no account holds, for which its {@code Change} writes {@code AuditLog.refused}
 *       naming no account, with the code M5 adds to {@code AccessError} for an address no account
 *       holds ({@code SignIn}'s domain gap);
 *   <li>a refusal of {@code UserAccount.issueLink} for a recovery link: {@code AccountDeactivated},
 *       {@code AccountAnonymized} or {@code AccountHasNoRole} (BR-123). Its {@code Change} catches
 *       the {@code RuleViolation} itself, and writes {@code AuditLog.refused} with the domain's
 *       specific code, naming the account. No {@code RuleViolation} leaves it, so its transaction
 *       commits with the record and changes nothing else (ADR-110);
 *   <li>NFR-215's cap of 100 recovery emails a day, reached;
 *   <li>a version conflict on the save, where a concurrent request for the same account won and is
 *       issuing the link, so no {@code StaleVersion} leaves the use case.
 * </ul>
 *
 * <p>The answer is the same; the work behind it is not. Only a request that issues a link saves the
 * account and hands the email over. No password is hashed on this path, so there is no slow step to
 * even out as {@code SignIn} does, and no answer waits for the email to be sent (NFR-225).
 *
 * <p><b>The two limits.</b> FR-172's limit is the web adapter's Bucket4j limit, keyed by the
 * canonical address (ADR-071), so a request over it never reaches this use case. NFR-215's cap is
 * counted on the send path, through {@link RecoveryEmailCap}: the use case takes from it only when
 * it is about to send a link, on the Israel day of the clock's instant (ADR-088), and sends nothing
 * when the take is refused. So an address no account holds, or a refused one, never uses up the
 * cap. The residual risk: about 20 real addresses, each at FR-172's 5 an hour, can still spend the
 * day's cap within an hour; ADR-036 revisits the limits on a day the cap is reached.
 *
 * <p><b>The order.</b> Nothing changes the account before the cap allows the email. The account
 * is a managed entity, so a change made to it in memory would be flushed at the commit whether or
 * not it is saved. So the transaction works in this order:
 * <ol>
 *   <li>It checks, without changing the account, that it may be sent a recovery link: the same
 *       refusals {@code UserAccount.issueLink} makes. The domain gains that check at M6.
 *   <li>It calls {@link RecoveryEmailCap#take}.
 *   <li>Only then does it issue the link and save.
 * </ol>
 * <p>On a refused check or take the account is left as it was, so its earlier open link, if it has
 * one, stays valid. The take is in the same transaction as the save, so a version conflict on the
 * save rolls the take back and a lost race spends none of the cap. The email goes to {@code
 * EmailSender.sendLink} only after that transaction commits, as every Access email does, so a
 * rolled-back save sends nothing.
 *
 * <p><b>The transaction.</b> {@link RequestPasswordRecoveryChange} holds it: the account's lookup,
 * the link's issue, the take and the save, and the audit record. The use case answers its {@code
 * StaleVersion} as a request that sent nothing, and after its commit hands the link it returns to
 * {@code EmailSender.sendLink} (ADR-129).
 *
 * <p>Like every changing call it writes its audit record, a refused attempt's in its own
 * transaction (NFR-23, ADR-110).
 *
 * <p>Built in M6.
 */
public final class RequestPasswordRecovery {

    private final EmailSender emailSender;
    private final RequestPasswordRecoveryChange change;

    /**
     * Creates the use case.
     *
     * @param emailSender sends links and notices by email
     * @param change the transaction that issues the recovery link
     */
    public RequestPasswordRecovery(EmailSender emailSender, RequestPasswordRecoveryChange change) {
        this.emailSender = emailSender;
        this.change = change;
    }

    /**
     * Sends the recovery link, when one may be sent and the day's cap has room. The caller answers
     * "sent" whether or not a link went out.
     *
     * <p>Built in M6.
     *
     * @param email the address typed
     * @param call the call's context: no user or session, the client and the trace (ADR-110)
     */
    public void request(EmailAddress email, CallContext call) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
