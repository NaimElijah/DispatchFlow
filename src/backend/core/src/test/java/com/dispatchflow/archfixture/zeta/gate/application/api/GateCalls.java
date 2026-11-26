package com.dispatchflow.archfixture.zeta.gate.application.api;

/**
 * Rule 17: another class of the call's own module builds a call naming whatever caller it is given,
 * which the rule forbids: only the call's own type may, for a server step that names no one.
 */
public final class GateCalls {

    private GateCalls() {}

    /** A call naming the given caller. */
    public static GateCall naming(String user) {
        return new GateCall(user);
    }
}
