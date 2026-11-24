package com.dispatchflow.access.application.api;

import com.dispatchflow.audit.application.api.CallContext;
import com.dispatchflow.shared.AccountId;
import java.util.List;

/**
 * The push addresses of live sessions, which Access keeps (placement P1): Live Operations' {@code
 * PushNotifier} adapter sends a driver's notice to an account's addresses, and an alert to the
 * staff's (FR-405, ADR-026, ADR-083). Sign-out and revocation remove an address, and so does the
 * adapter through {@link #forget} when the push service reports the device gone (NFR-263).
 *
 * <p>To Be Implemented.
 */
public interface PushTargets {

    /**
     * The push addresses registered for an account's live sessions.
     *
     * <p>To Be Implemented.
     *
     * @param account the account
     * @return its addresses, one per live session that registered one; empty when there is none
     */
    List<PushAddress> of(AccountId account);

    /**
     * The push addresses of the staff's phones, which an alert goes to while the app is in the
     * background (FR-405, ADR-083): those of the live sessions of every active account that holds
     * the Admin or the Dispatcher role, read now.
     *
     * <p>To Be Implemented.
     *
     * @return the addresses, each once; empty when no such session registered one
     */
    List<PushAddress> ofStaff();

    /**
     * Removes an address the push service reported gone, from every session that registered it,
     * and writes the audit record, which names those sessions (NFR-263, ADR-083, ADR-110). An
     * address no session holds any longer changes nothing, so a repeated report is harmless.
     *
     * <p>To Be Implemented.
     *
     * @param address the address the push service reported gone
     * @param call the adapter's server step
     */
    void forget(PushAddress address, CallContext call);
}
