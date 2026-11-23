package com.dispatchflow;

import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs integration-test work in its own transaction, as a use case's would. A test that proves a
 * conflict runs one change {@link #inANewTransaction in a new transaction} while an outer one still
 * holds its copy, on one thread and with no sleep (ADR-126). {@link TestPersistenceConfiguration}
 * provides it as a bean.
 */
public final class Transactions {

    private final PlatformTransactionManager manager;

    Transactions(PlatformTransactionManager manager) {
        this.manager = manager;
    }

    /**
     * Runs work in a transaction, joining one already open, and commits it.
     *
     * @param work the work, which returns a value
     * @param <T> the value's type
     * @return what the work returned
     * @throws AssertionError when the work returned null, a bug in the test
     */
    public <T> T inTransaction(Supplier<T> work) {
        T result = new TransactionTemplate(manager).execute(status -> work.get());
        if (result == null) {
            throw new AssertionError("the transaction returned nothing");
        }
        return result;
    }

    /**
     * Runs work in a transaction, joining one already open, and commits it.
     *
     * @param work the work
     */
    public void inTransaction(Runnable work) {
        new TransactionTemplate(manager).executeWithoutResult(status -> work.run());
    }

    /**
     * Runs work in a new transaction that commits on its own, while any outer one waits.
     *
     * @param work the work
     */
    public void inANewTransaction(Runnable work) {
        TransactionTemplate requiresNew = new TransactionTemplate(manager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(status -> work.run());
    }
}
