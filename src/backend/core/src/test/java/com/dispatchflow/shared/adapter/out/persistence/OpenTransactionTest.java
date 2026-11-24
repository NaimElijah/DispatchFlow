package com.dispatchflow.shared.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link OpenTransaction} against the thread's transaction state, set directly as Spring's
 * transaction manager sets it, so no database or context is needed.
 */
class OpenTransactionTest {

    @AfterEach
    void clearTheThreadsTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void GivenNoTransaction_WhenRequired_ThenTheCallIsRefused() {
        assertThatThrownBy(() -> OpenTransaction.require("capOn"))
                .isInstanceOf(IllegalTransactionStateException.class)
                .hasMessageContaining("capOn");
    }

    @Test
    void GivenAReadOnlyTransaction_WhenRequired_ThenTheCallIsRefused() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThatThrownBy(() -> OpenTransaction.require("capacityUnderLock"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void GivenAReadWriteTransaction_WhenRequired_ThenTheCallGoesAhead() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        assertThatCode(() -> OpenTransaction.require("claimNext")).doesNotThrowAnyException();
    }
}
