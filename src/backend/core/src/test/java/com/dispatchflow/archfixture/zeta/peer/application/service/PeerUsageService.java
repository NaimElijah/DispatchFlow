package com.dispatchflow.archfixture.zeta.peer.application.service;

import com.dispatchflow.archfixture.zeta.lower.application.spi.PeerUsage;
import com.dispatchflow.archfixture.zeta.lower.application.spi.UsageQuestion;

/**
 * Rules 1 and 4: the one implementation of {@code PeerUsage}, in the module its arrow names, using
 * the record beside it in {@code lower}'s spi.
 */
public final class PeerUsageService implements PeerUsage {

    @Override
    public boolean inUse(UsageQuestion question) {
        return question.thing() > 0;
    }
}
