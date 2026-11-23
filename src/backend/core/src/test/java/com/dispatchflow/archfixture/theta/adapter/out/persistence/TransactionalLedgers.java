package com.dispatchflow.archfixture.theta.adapter.out.persistence;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/** Rule 1: a persistence adapter that opens its own transaction. */
@Transactional
public class TransactionalLedgers implements Ledgers {

    @Override
    public long count() {
        return 0;
    }
}
