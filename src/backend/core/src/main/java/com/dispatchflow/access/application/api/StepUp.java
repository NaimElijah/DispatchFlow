package com.dispatchflow.access.application.api;

import com.dispatchflow.shared.RuleViolation;

/**
 * The recent-password check that every Admin-only change needs (NFR-214, ADR-068, ADR-104). Access
 * owns sessions, so a use case in another module that changes Admin-only settings, such as the
 * planning or messaging settings, asks it before its change.
 *
 * <p>To Be Implemented.
 */
public interface StepUp {

    /**
     * Requires the actor's session to have confirmed the password within the step-up window
     * ({@code Session.requireFreshStepUp}).
     *
     * <p>To Be Implemented.
     *
     * @param actor the acting Admin
     * @throws RuleViolation with {@code SessionRevoked} or {@code StepUpRequired}, in that order
     */
    void requireFresh(Actor actor);
}
