package com.dispatchflow.access.application.api;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.application.NotFound;

/**
 * Access's side of retiring a driver: Fleet's listener on {@code DriverRetired} calls it to take
 * the Driver role off the driver's account, keeping its other roles (FR-307, ADR-104). Like every
 * role change it revokes the account's sessions (NFR-90).
 *
 * <p>Built in M2.
 */
public interface RemoveDriverRole {

    /**
     * Removes the Driver role. Delivery of the event is at least once, so the call works in every
     * state of the account, and a repeat changes nothing (ADR-104).
     *
     * <p>Built in M2.
     *
     * @param driver the retired driver's account
     * @param call the listener's server step
     * @throws NotFound when no account has the ID
     * @throws com.dispatchflow.shared.application.StaleVersion when the account changed between its read
     *     and its save (BR-76); the caller's listener or job adapter retries at once, up to three
     *     times (DL §11)
     */
    void remove(AccountId driver, CallContext call);
}
