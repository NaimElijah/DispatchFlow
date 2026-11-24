package com.dispatchflow.archfixture.theta.adapter.out.persistence;

import com.dispatchflow.archfixture.theta.application.port.out.Ledgers;
import org.springframework.transaction.annotation.Transactional;

/** Rule 1: a persistence adapter whose method opens its own transaction. */
public class LedgersWithATransactionalMethod implements Ledgers {

    @Override
    @Transactional
    public long count() {
        return 0;
    }
}
