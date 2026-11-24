package com.dispatchflow.access.application.service.account.credentiallink;

import com.dispatchflow.audit.application.api.CallContext;

/**
 * The nightly retention job's link step: it drops every credential link that no longer works and
 * was sent more than {@code UserAccount.LINK_KEPT_FOR} ago, the longest any link works (ADR-104).
 * {@code UserAccount.issueLink} drops such links only when the account is sent a new one, so
 * without this step an account sent no further link would keep them, and an email-change link
 * would keep its unconfirmed new address with no deadline. A link sent that long ago has
 * outlived its lifetime, so the step never drops a link that still works.
 *
 * <p>It finds the accounts through {@code UserAccountRepository.withLinksSentBefore}, with the
 * cutoff {@code LINK_KEPT_FOR} before the clock's instant. Each account is loaded, changed and
 * saved in its own transaction, with its audit record naming the account (NFR-93), so one stale
 * save doesn't hold back the rest; the next night retries it.
 *
 * <p><b>The transactions.</b> The job lists the accounts through {@link DropOldLinksChange#due}, a
 * read-only transaction, then hands each to {@link DropOldLinksChange#apply}, one account's
 * transaction. A {@code StaleVersion} from one account is left for the next night, and the job goes
 * on to the next (ADR-129).
 *
 * <p>Domain gap: {@code UserAccount} drops old links only inside {@code issueLink}. Building
 * this job adds {@code UserAccount.dropOldLinks(now)}, the same pruning without a new link, in any
 * state of the account, as {@code Session.dropOldContacts} works on any session; it moves no
 * generation and raises no event.
 *
 * <p>To Be Implemented. It comes with the whole nightly retention run (ADR-121).
 */
public final class DropOldLinks {

    private final DropOldLinksChange change;

    /**
     * Creates the job.
     *
     * @param change the transactions that list the accounts and drop one account's old links
     */
    public DropOldLinks(DropOldLinksChange change) {
        this.change = change;
    }

    /**
     * Drops the old links of every account that holds one.
     *
     * <p>To Be Implemented.
     *
     * @param call the job's server step
     * @return how many accounts dropped links
     */
    public int dropDue(CallContext call) {
        throw new UnsupportedOperationException("To Be Implemented.");
    }
}
