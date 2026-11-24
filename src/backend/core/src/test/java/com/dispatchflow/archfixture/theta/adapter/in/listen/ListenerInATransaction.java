package com.dispatchflow.archfixture.theta.adapter.in.listen;

/** Rule 1: a listener whose annotation opens a transaction around it. */
public class ListenerInATransaction {

    /** Reacts inside a transaction its annotation opens. */
    @OpensATransaction
    public void on() {}
}
