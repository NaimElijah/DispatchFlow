package com.dispatchflow.archfixture.theta.application.api;

/** The audit port, named like production's, which only a changing service holds. */
public interface AuditLog {

    /** Records that a call succeeded. */
    void succeeded();
}
