package com.dispatchflow.archfixture.shared.application;

import jakarta.persistence.Transient;

/** Rule 1: the kernel's application ports carry no persistence annotation (ADR-126). */
public final class SharedPortCarriesAJpaAnnotation {

    @Transient
    private int cached;

    int cached() {
        return cached;
    }
}
