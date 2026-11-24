package com.dispatchflow.access.application.api;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;

/**
 * Whether an access token's generations are still current, which revokes a token without a
 * denylist (ADR-045, NFR-267): the security filter and the event stream's heartbeat ask it. The
 * answer may come from the revocation cache (NFR-177).
 *
 * <p>To Be Implemented.
 */
public interface SessionValidity {

    /**
     * Whether the token's account and session generations are both the current ones.
     *
     * <p>To Be Implemented.
     *
     * @param account the token's account
     * @param accountGeneration the account generation the token carries
     * @param session the token's session
     * @param sessionGeneration the session generation the token carries
     * @return {@code true} when the account is active, the session live, and both generations equal
     *     the stored ones; {@code false} otherwise, an unknown account or session included
     */
    boolean isCurrent(AccountId account, long accountGeneration, SessionId session, long sessionGeneration);
}
