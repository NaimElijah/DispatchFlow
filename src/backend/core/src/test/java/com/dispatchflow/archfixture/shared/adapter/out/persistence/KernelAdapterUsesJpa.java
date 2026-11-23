package com.dispatchflow.archfixture.shared.adapter.out.persistence;

import jakarta.persistence.EntityManager;
import java.util.logging.Logger;

/**
 * Allowed: the shared adapter is an adapter, not the kernel, so it may use an EntityManager and
 * log (ADR-126).
 */
public final class KernelAdapterUsesJpa {

    private static final Logger LOG = Logger.getLogger(KernelAdapterUsesJpa.class.getName());

    static void flush(EntityManager entityManager) {
        LOG.fine("flushing");
        entityManager.flush();
    }
}
