package com.dispatchflow.archfixture.theta.adapter.in.listen;

import org.springframework.transaction.event.TransactionalEventListener;

/** Rule 8: a listener that runs synchronously after the commit. */
public class ListensWithoutAsync {

    /** Reacts on the thread whose transaction just committed. */
    @TransactionalEventListener
    public void on(Object event) {}
}
