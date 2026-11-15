package com.dispatchflow.access.application.api;

import com.dispatchflow.shared.AccountId;
import java.util.List;

/**
 * The push addresses of an account's live sessions, which Access keeps (placement P1): Live
 * Operations' {@code PushNotifier} adapter sends a driver's or a dispatcher's notice to them
 * (ADR-026, ADR-083). Sign-out and revocation remove an address (NFR-263).
 *
 * <p>Built in M10.
 */
public interface PushTargets {

    /**
     * The push addresses registered for an account's live sessions.
     *
     * <p>Built in M10.
     *
     * @param account the account
     * @return its addresses, one per live session that registered one; empty when there is none
     */
    List<PushAddress> of(AccountId account);
}
