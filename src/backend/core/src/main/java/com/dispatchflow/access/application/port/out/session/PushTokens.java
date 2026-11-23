package com.dispatchflow.access.application.port.out.session;

import com.dispatchflow.access.application.api.PushAddress;
import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;
import java.util.List;

/**
 * The push addresses of signed-in phones, one per session, which Access keeps (placement P1,
 * ADR-026, ADR-083). An address is removed at sign-out, at revocation and when the push service
 * reports the device gone (NFR-263).
 *
 * <p>Built in M10.
 */
public interface PushTokens {

    /**
     * Records a session's push address, replacing the one it had.
     *
     * <p>Built in M10.
     *
     * @param session the phone's session
     * @param account the session's account
     * @param address the push service's token for the device
     */
    void register(SessionId session, AccountId account, PushAddress address);

    /**
     * Removes a session's address, at sign-out, when the session is revoked (NFR-263), and when the
     * retention job deletes it. Removing a missing one changes nothing.
     *
     * <p>Built in M10.
     *
     * @param session the session
     */
    void removeFor(SessionId session);

    /**
     * Removes an address from every session that holds it, when the push service reports the device
     * gone (NFR-263). Removing one no session holds changes nothing.
     *
     * <p>Built in M10.
     *
     * @param address the address reported gone
     * @return the sessions it was removed from, each once; empty when none held it
     */
    List<SessionId> removeAddress(PushAddress address);

    /**
     * The addresses of an account's live sessions.
     *
     * <p>Built in M10.
     *
     * @param account the account
     * @return the addresses, possibly none
     */
    List<PushAddress> of(AccountId account);

    /**
     * The addresses of the live sessions of every active account that holds the Admin or the
     * Dispatcher role, read in one query over Access's own accounts and sessions (FR-405, ADR-083).
     *
     * <p>Built in M10.
     *
     * @return the addresses, each once, possibly none
     */
    List<PushAddress> ofActiveStaff();
}
