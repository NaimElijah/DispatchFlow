package com.dispatchflow.archfixture.theta.adapter.in.listen;

import org.springframework.scheduling.annotation.Async;
import org.springframework.transaction.event.TransactionalEventListener;

/** Clean: a listener that reacts on its own thread, so the service it calls opens a fresh transaction. */
public class ListensAsync {

    /** Reacts after the commit, on another thread. */
    @Async
    @TransactionalEventListener
    public void on(Object event) {}
}
