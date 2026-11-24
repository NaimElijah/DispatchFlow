package com.dispatchflow.archfixture.shared.adapter.out.uuid;

import java.util.UUID;

/**
 * Allowed: the shared ID adapter is no part of the kernel, so it may generate an ID (ADR-130
 * decision 5).
 */
public final class RandomIdsDrawAUuid {

    private RandomIdsDrawAUuid() {}

    static UUID next() {
        return UUID.randomUUID();
    }
}
