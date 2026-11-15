package com.dispatchflow.access.application.service.session;

import com.dispatchflow.access.application.api.PushAddress;
import com.dispatchflow.access.application.api.PushTargets;
import com.dispatchflow.access.application.port.out.PushTokens;
import com.dispatchflow.shared.AccountId;
import java.util.List;

/**
 * Access's implementation of {@code PushTargets}: the push addresses of an account's live sessions,
 * for Live Operations' {@code PushNotifier} (ADR-026, ADR-083, placement P1).
 *
 * <p>Built in M10.
 */
public final class PushTargetsService implements PushTargets {

    private final PushTokens pushTokens;

    /**
     * Creates the service.
     *
     * @param pushTokens the push addresses of signed-in phones
     */
    public PushTargetsService(PushTokens pushTokens) {
        this.pushTokens = pushTokens;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Built in M10.
     */
    @Override
    public List<PushAddress> of(AccountId account) {
        throw new UnsupportedOperationException("Built in M10");
    }
}
