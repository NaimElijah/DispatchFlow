package com.dispatchflow.fleet.application.service.driver;

import com.dispatchflow.access.application.api.RemoveDriverRole;
import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.fleet.events.DriverRetired;
import com.dispatchflow.shared.application.NotFound;
import com.dispatchflow.shared.application.StaleVersion;

/**
 * Fleet's listener on its own {@code DriverRetired}: it calls Access's {@code RemoveDriverRole},
 * which takes the Driver role off the account and keeps its other roles (FR-307, ADR-103, DL §2.3
 * rule 2). Access's call works in every state and a repeat changes nothing, so a redelivered event
 * is harmless (ADR-064). It changes no Fleet aggregate, so it writes no audit record itself: it
 * passes its server step to Access, which writes the record with it (ADR-110).
 *
 * <p>It opens no transaction of its own: Access's {@code RemoveDriverRole} is a use case that
 * opens its own, and evicts the account's cached generation only after that commit (ADR-045,
 * ADR-129).
 *
 * <p>Built in M2.
 */
public final class RemoveRoleOfRetiredDriver {

    private final RemoveDriverRole removeDriverRole;

    /**
     * Creates the listener.
     *
     * @param removeDriverRole Access's command that removes the Driver role
     */
    public RemoveRoleOfRetiredDriver(RemoveDriverRole removeDriverRole) {
        this.removeDriverRole = removeDriverRole;
    }

    /**
     * Removes the Driver role.
     *
     * <p>Built in M2.
     *
     * @param event the retirement
     * @param call the listener's server step
     * @throws NotFound when no account has the driver's ID
     * @throws StaleVersion when the account, through Access's call, changed between its read and
     *     its save (BR-76); the caller's listener or job adapter retries at once, up to three times
     *     (DL §11)
     */
    public void on(DriverRetired event, CallContext call) {
        throw new UnsupportedOperationException("Built in M2");
    }
}
