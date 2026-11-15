package com.dispatchflow.access.application.port.out;

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
     * Removes a session's address, at sign-out (NFR-263). Removing a missing one changes nothing.
     *
     * <p>Built in M10.
     *
     * @param session the session
     */
    void removeFor(SessionId session);

    /**
     * Removes every address of an account's sessions, at revocation (NFR-263).
     *
     * <p>Built in M10.
     *
     * @param account the account
     */
    void removeAllFor(AccountId account);

    /**
     * The addresses of an account's live sessions.
     *
     * <p>Built in M10.
     *
     * @param account the account
     * @return the addresses, possibly none
     */
    List<PushAddress> of(AccountId account);
}
