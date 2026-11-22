package com.dispatchflow.access.application.service.account.credentiallink;

import com.dispatchflow.access.application.port.out.EmailSender;
import com.dispatchflow.access.application.port.out.PresentedToken;
import com.dispatchflow.access.application.port.out.RevocationCache;
import com.dispatchflow.access.application.port.out.SecretTokens;
import com.dispatchflow.access.application.port.out.UserAccountRepository;
import com.dispatchflow.audit.application.api.AuditLog;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.RuleViolation;
import com.dispatchflow.shared.application.Clock;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Confirms a new email through the link sent to it: the address takes effect, the link is spent,
 * every other open link is voided (FR-388, NFR-224), and every session ends (FR-392). The old
 * address is told, without naming the new one (FR-388). The change moves the account's generation
 * and raises {@code SessionsRevoked}, so every token issued before is refused at once (NFR-90,
 * NFR-177); the use case evicts the account from the revocation cache after its commit (ADR-045).
 * Every active Admin is emailed when it is an Admin's new email (FR-389): the use case reads them
 * through {@code UserAccountRepository.activeAdminRecipients} and sends {@code
 * EmailSender.sendAdminNotice}.
 *
 * <p><b>Refusals answered as a voided link.</b> The holder of the new mailbox may not be the
 * account's user, so three of {@code UserAccount.confirmEmailByLink}'s refusals must not tell them
 * more than that the link no longer works. The use case catches each, writes {@code
 * AuditLog.refused} with the domain's specific code in its own transaction (ADR-110), and then
 * throws a {@code RuleViolation} with Access's {@code LinkVoided} in its place:
 *
 * <ul>
 *   <li>{@code EmailOfAnotherAdmin}, so the holder learns nothing about the account's roles
 *       (ADR-116);
 *   <li>{@code AccountDeactivated} and {@code AccountAnonymized}, so the holder learns nothing
 *       about the account's state. A deactivation voids every unspent link (NFR-224), so for an
 *       unspent link {@code LinkVoided} is what happened to it.
 * </ul>
 *
 * <p><b>Gap for M6.</b> {@code UserAccount} checks the account's state before the link's, so a spent
 * link would tell an active account from a deactivated one. M6 checks the link's state first, so a
 * spent link answers alike in every state (NFR-145).
 *
 * <p>{@code EmailInUse} is answered as itself: FR-392 refuses an address another account holds at
 * this step, and only here. Like every changing call it writes its audit record, a refused
 * attempt's in its own transaction (NFR-23, ADR-110).
 *
 * <p>Built in M6.
 */
public final class ConfirmEmailByLink {

    private final UserAccountRepository accounts;
    private final SecretTokens secretTokens;
    private final RevocationCache revocationCache;
    private final EmailSender emailSender;
    private final Clock clock;
    private final AuditLog auditLog;

    /**
     * Creates the use case.
     *
     * @param accounts where accounts are kept
     * @param secretTokens makes refresh and link tokens, and hashes a presented one
     * @param revocationCache the token check's cache, evicted after a change
     * @param emailSender sends links and notices by email
     * @param clock the time
     * @param auditLog where the audit record is written
     */
    public ConfirmEmailByLink(
            UserAccountRepository accounts,
            SecretTokens secretTokens,
            RevocationCache revocationCache,
            EmailSender emailSender,
            Clock clock,
            AuditLog auditLog) {
        this.accounts = accounts;
        this.secretTokens = secretTokens;
        this.revocationCache = revocationCache;
        this.emailSender = emailSender;
        this.clock = clock;
        this.auditLog = auditLog;
    }

    /**
     * Confirms the new address and spends the link.
     *
     * <p>Built in M6.
     *
     * @param token the token the link carried
     * @param call the call's context: no user or session, the client and the trace (ADR-110)
     * @throws RuleViolation with Access's {@code UnknownLink} when no email-change link has the
     *     token; {@code LinkAlreadyUsed} or {@code LinkExpired} when the link no longer works;
     *     {@code LinkVoided} when it was voided, and in place of {@code EmailOfAnotherAdmin} (the
     *     account holds the Admin role and another account asked; FR-387, ADR-116), {@code
     *     AccountDeactivated} or {@code AccountAnonymized}, as above; {@code EmailInUse} when
     *     another account holds the address (FR-392)
     * @throws StaleVersion when the account changed between its read and its save (BR-76); the web
     *     adapter retries the call at once, up to three times, then answers 503 with {@code
     *     Retry-After} (ADR-121)
     */
    public void confirm(PresentedToken token, CallContext call) {
        throw new UnsupportedOperationException("Built in M6");
    }
}
