package com.dispatchflow.shared.adapter.out.persistence;

import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The check a persistence adapter makes before it takes a lock that lasts only as long as its
 * transaction: a {@code pg_advisory_xact_lock}, a {@code FOR UPDATE} or a {@code SKIP LOCKED}
 * (ADR-129 decision 6). Outside a transaction the database releases such a lock when its statement
 * ends, so the check the lock guards would protect nothing. The adapters carry no
 * {@code @Transactional}; the service that calls them opens the transaction (ADR-129 decision 1).
 */
public final class OpenTransaction {

    private OpenTransaction() {}

    /**
     * Requires a transaction open on this thread.
     *
     * @param method the adapter method that takes the lock, named in the error
     * @throws IllegalTransactionStateException when none is open: the error Spring's {@code
     *     MANDATORY} propagation throws, so a caller sees the same refusal either way
     */
    public static void require(String method) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalTransactionStateException(
                    method + " takes a lock that lasts only as long as its transaction, and none is open");
        }
    }
}
