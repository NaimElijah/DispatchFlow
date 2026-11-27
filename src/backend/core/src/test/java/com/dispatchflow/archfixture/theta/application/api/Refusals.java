package com.dispatchflow.archfixture.theta.application.api;

/** The audit helper, named like production's, that runs an attempt and records its refusal. */
public final class Refusals {

    private Refusals() {}

    /**
     * Runs the attempt, and records the refusal that leaves it.
     *
     * @param auditLog the audit trail
     * @param attempt the attempt
     */
    public static void recordedBy(AuditLog auditLog, Runnable attempt) {
        try {
            attempt.run();
        } catch (IllegalStateException refusal) {
            auditLog.refused();
            throw refusal;
        }
    }
}
