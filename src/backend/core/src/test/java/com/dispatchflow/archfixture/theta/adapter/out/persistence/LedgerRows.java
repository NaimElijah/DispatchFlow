package com.dispatchflow.archfixture.theta.adapter.out.persistence;

import org.springframework.data.jpa.repository.Query;

/** The queries the theta adapters call: one takes a transaction-scoped lock, one doesn't. */
interface LedgerRows {

    /** Takes the ledgers' advisory lock, held until the transaction ends. */
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(hashtext('theta:ledgers'))", nativeQuery = true)
    void lockLedgers();

    /**
     * Counts the ledgers, taking no lock.
     *
     * @return how many there are
     */
    @Query(value = "SELECT count(*) FROM theta.ledger", nativeQuery = true)
    long countAll();

    /**
     * Counts the ledgers while locking their rows against a key change, with the {@code FOR} and its
     * lock strength on separate lines.
     *
     * @return how many there are
     */
    @Query(value = """
            SELECT count(*) FROM theta.ledger FOR NO KEY
                UPDATE
            """, nativeQuery = true)
    long countLockingTheRows();
}
