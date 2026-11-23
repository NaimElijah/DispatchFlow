package com.dispatchflow.archfixture.alpha.domain.mapping;

import jakarta.persistence.EntityManager;

/** Rule 1: a domain carries persistence annotations only, never an EntityManager (ADR-126). */
public final class UsesTheEntityManager {

    static void flush(EntityManager entityManager) {
        entityManager.flush();
    }
}
