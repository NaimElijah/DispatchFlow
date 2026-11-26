package com.dispatchflow.archfixture.zeta.gate.application.api;

/**
 * Rule 17: stands in for Audit's {@code CallContext}, which names the caller. Its own module builds
 * one for a server step, which the rule allows.
 *
 * @param user the caller it names
 */
public record GateCall(String user) {

    /** A server step's call, which names no one. */
    public static GateCall serverStep() {
        return new GateCall("");
    }
}
