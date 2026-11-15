package com.dispatchflow.access.application.port.out;

import com.dispatchflow.access.domain.Language;
import com.dispatchflow.access.domain.account.EmailAddress;
import com.dispatchflow.access.domain.account.LinkKind;

/**
 * Sends Access's emails: credential links and notices, through Amazon SES, and Mailpit in
 * development and tests (TS §6.4). No answer waits for an email to be sent (NFR-225); whether
 * emails get an outbox is decided in M6.
 *
 * <p>Every email goes to the account's own address (BR-125) and carries no secret but a link's
 * token, which never enters a log (NFR-19, NFR-211).
 *
 * <p>Built in M6.
 */
public interface EmailSender {

    /**
     * Sends a set-password, recovery or email-change link (FR-294, FR-386, FR-388). A recovery
     * link reaches this port only once FR-172's limit, the web adapter's Bucket4j limit (ADR-071),
     * let the request through, and the use case took it from NFR-215's {@link RecoveryEmailCap}.
     *
     * <p>Built in M6.
     *
     * @param to the address the link goes to
     * @param kind what the link lets its holder do
     * @param token the link's token
     * @param language the language the email is written in
     */
    void sendLink(EmailAddress to, LinkKind kind, IssuedToken token, Language language);

    /**
     * Tells the old address that the account's email changed, without naming the new one (FR-388).
     *
     * <p>Built in M6.
     *
     * @param oldAddress the address the account no longer signs in with
     * @param language the language the email is written in
     */
    void sendEmailChangedNotice(EmailAddress oldAddress, Language language);

    /**
     * Tells every active Admin that an account gained the Admin role, an Admin account was
     * reactivated, or an Admin's password or email changed (FR-389).
     *
     * <p>Built in M6.
     *
     * @param notice what happened, and to whom it goes
     */
    void sendAdminNotice(AdminNotice notice);
}
