package com.dispatchflow.web;

import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionExecution;
import org.springframework.transaction.TransactionExecutionListener;

/**
 * Marks a try of a call committed when one of its transactions commits, so {@link CallRetry} never
 * re-runs a call whose change is already stored, and answers any race after it 500 (ADR-140
 * decision 6, ADR-142). Spring Boot gives every {@code TransactionExecutionListener} bean to the
 * transaction manager. A transaction synchronization can't do this: the wrapper runs outside every
 * transaction, where spring-tx refuses one.
 *
 * <p>A read-only commit changes nothing, and a split use case reads in read-only transactions
 * before its change (ADR-129), so only a read-write commit marks the try (ADR-142). A commit that
 * failed with a {@code TransactionException} marks it too: spring-tx reports it through {@code
 * afterCommit} with the failure, and its outcome is unknown. A commit failure that spring-orm
 * translates into a {@code DataAccessException} reaches the listeners as {@code afterRollback}
 * instead, once spring-tx has rolled the transaction back, so it doesn't mark the try; that is
 * harmless, because the exception ends the try.
 */
@Component
class CommitWatch implements TransactionExecutionListener {

    /** The try of the call running on this thread; unbound outside a call and on other threads. */
    static final ScopedValue<Try> CURRENT = ScopedValue.newInstance();

    @Override
    public void afterCommit(TransactionExecution transaction, @Nullable Throwable commitFailure) {
        if (CURRENT.isBound() && !transaction.isReadOnly()) {
            CURRENT.get().markCommitted();
        }
    }

    /** One try of a call, and whether a transaction of it committed. */
    static final class Try {

        private volatile boolean committed;

        /** Whether a read-write transaction of this try committed, or tried to. */
        boolean committed() {
            return committed;
        }

        private void markCommitted() {
            committed = true;
        }
    }
}
