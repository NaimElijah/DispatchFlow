package com.dispatchflow.archfixture.theta.adapter.in.listen;

/** Clean: a listener with no transaction of its own, whose service would open one. */
public class PlainListener {

    /** Reacts, leaving the transaction to the service it calls. */
    public void on() {}
}
