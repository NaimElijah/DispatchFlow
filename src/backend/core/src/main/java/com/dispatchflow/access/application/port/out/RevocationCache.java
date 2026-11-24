package com.dispatchflow.access.application.port.out;

import com.dispatchflow.shared.AccountId;
import com.dispatchflow.shared.SessionId;

/**
 * The cache the token check reads generations and session states from (ADR-045, NFR-177): its
 * read side is {@code UserAccountRepository.activeGenerationOf} and {@code
 * SessionRepository.liveGenerationOf}, which the adapter answers through the cache's atomic loader.
 * This port is its write side: a use case that changes an account's generation, or a session's
 * generation or state, evicts the entry right after its commit, in the same thread, so the next
 * request sees the change. Entries also expire after 60 seconds.
 *
 * <p>An eviction reaches every core process. A server step runs in a process of its own, so the
 * adapter also sends the evicted key on a PostgreSQL channel ({@code NOTIFY}) when the
 * transaction commits, and the serving core, which listens on it, drops the same entry (ADR-131).
 *
 * <p>Built in M6.
 */
public interface RevocationCache {

    /**
     * Drops an account's entry.
     *
     * <p>Built in M6.
     *
     * @param account the account whose generation or state changed
     */
    void evict(AccountId account);

    /**
     * Drops a session's entry.
     *
     * <p>Built in M6.
     *
     * @param session the session whose generation or state changed
     */
    void evict(SessionId session);
}
