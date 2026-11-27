package com.dispatchflow.shared.adapter.out.persistence;

import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The check a persistence adapter makes before work that is only safe inside its caller's
 * read-write transaction (ADR-129 decision 6). Three kinds of work need it:
 * <ul>
 *   <li>A lock that lasts only as long as the transaction: a {@code pg_advisory_xact_lock}, a {@code
 *       FOR UPDATE} or a {@code SKIP LOCKED}. With no transaction the database releases it when its
 *       statement ends. In a read-only one, which a {@code Change}'s read opens, it is released when
 *       that read commits, before the write it guards. Either way the check it guards would protect
 *       nothing.
 *   <li>A write that would otherwise commit on its own, such as a {@code DELETE … RETURNING} that
 *       runs as a query.
 *   <li>A write a read-only transaction would drop without a word, because it never flushes: an
 *       audit record's append, which must never be lost (ADR-149 decision 3).
 * </ul>
 *
 * <p>The adapters carry no {@code @Transactional}; the service that calls them opens the
 * transaction (ADR-129 decision 1). It can't tell a listener that runs synchronously after a
 * commit, which still sees that transaction as open: that is why a listener is {@code @Async}
 * (decision 7).
 */
public final class OpenTransaction {

    private OpenTransaction() {}

    /**
     * Requires a read-write transaction open on this thread.
     *
     * @param method the adapter method that needs it, named in the error
     * @throws IllegalTransactionStateException when none is open, or the one open is read-only: the
     *     error Spring's {@code MANDATORY} propagation throws, so a caller sees the same refusal either
     *     way
     */
    public static void require(String method) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalTransactionStateException(
                    method + " needs its caller's read-write transaction, and none is open");
        }
    }
}
