package com.dispatchflow.archfixture.theta.adapter.out.persistence;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import jakarta.transaction.Transactional;

/** Rule 1: a persistence adapter that opens its own transaction through Jakarta's annotation. */
@Transactional
public class JakartaTransactionalLedgers implements Ledgers {

    @Override
    public long count() {
        return 0;
    }
}
